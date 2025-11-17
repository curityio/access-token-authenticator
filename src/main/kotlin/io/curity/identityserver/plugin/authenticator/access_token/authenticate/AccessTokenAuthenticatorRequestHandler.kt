/*
 * Copyright (C) 2025 Curity AB. All rights reserved.
 *
 * The contents of this file are the property of Curity AB.
 * You may not copy or use this file, in either source code
 * or executable form, except in compliance with terms
 * set by Curity AB.
 *
 * For further information, please contact Curity AB.
 */

package io.curity.identityserver.plugin.authenticator.access_token.authenticate

import io.curity.identityserver.plugin.authenticator.access_token.descriptor.AccessTokenAuthenticatorConfig
import io.curity.identityserver.plugin.authenticator.access_token.descriptor.AccessTokenAuthenticatorConstants
import jakarta.validation.constraints.NotEmpty
import org.jose4j.jwt.GeneralJwtException
import org.jose4j.jwt.JwtClaims
import org.jose4j.jwt.consumer.InvalidJwtException
import org.jose4j.jwt.consumer.InvalidJwtSignatureException
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.slf4j.LoggerFactory
import se.curity.identityserver.sdk.Nullable
import se.curity.identityserver.sdk.authentication.AuthenticationResult
import se.curity.identityserver.sdk.authentication.AuthenticatorRequestHandler
import se.curity.identityserver.sdk.errors.ErrorCode
import se.curity.identityserver.sdk.haapi.ProblemContract
import se.curity.identityserver.sdk.http.MediaType
import se.curity.identityserver.sdk.oauth.OAuthClient
import se.curity.identityserver.sdk.service.ExceptionFactory
import se.curity.identityserver.sdk.web.Request
import se.curity.identityserver.sdk.web.Response
import se.curity.identityserver.sdk.web.Response.ResponseModelScope.NOT_FAILURE
import se.curity.identityserver.sdk.web.ResponseModel
import se.curity.identityserver.sdk.web.alerts.ErrorMessage
import java.util.Optional

/**
 * Request model for the access_token authenticator request handler.
 *
 * The only requirement is that on POST requests, a "token" is provided as a form parameter.
 */
sealed interface AccessTokenAuthenticatorRequestModel {
    object GetRequestModel : AccessTokenAuthenticatorRequestModel
    class PostRequestModel(
        @get:NotEmpty("token is mandatory")
        val token: String?,
    ) : AccessTokenAuthenticatorRequestModel

    companion object {
        fun forGet() = GetRequestModel

        fun forPost(request: Request) = PostRequestModel(request.getFormParameterValueOrError("token"))
    }
}

/**
 * The access_token authenticator request handler.
 *
 * This request handler expects the client to provide a valid access token.
 * It then validates the access token's signature and claims according to this plugin's configuration.
 * If everything is correct, the user is authenticated with the subject of the access token.
 *
 * @see [HaapiAccessTokenRepresentationFunction]
 */
class AccessTokenAuthenticatorRequestHandler(
    private val _config: AccessTokenAuthenticatorConfig,
    private val _exceptionFactory: ExceptionFactory,
) : AuthenticatorRequestHandler<AccessTokenAuthenticatorRequestModel> {

    companion object {
        private val _logger = LoggerFactory.getLogger(AccessTokenAuthenticatorRequestHandler::class.java)
    }

    override fun preProcess(
        request: Request,
        response: Response,
    ): AccessTokenAuthenticatorRequestModel {
        enforceHaapiFlow(request, response)
        checkIfOAuthClientIsAllowed(response)
        return if (request.isGetRequest) AccessTokenAuthenticatorRequestModel.forGet()
        else AccessTokenAuthenticatorRequestModel.forPost(request)
    }

    private fun enforceHaapiFlow(request: Request, response: Response) {
        if (request.acceptableMediaTypes != MediaType.HAAPI_JSON.toString()) {
            failAuthentication(
                response, "Request must accept only the Media-Type ${MediaType.HAAPI_JSON} to call this endpoint",
            )
        }
    }

    private fun checkIfOAuthClientIsAllowed(response: Response) {
        val clientNotAllowed = "OAuth client is not allowed"

        val oauthClient: @Nullable OAuthClient = _config.requestingOAuthClient.client
            ?: failAuthentication(
                response, clientNotAllowed,
                detailedMessage = "The authorization flow was not started by a known OAuth Client, cannot proceed."
            )

        if (oauthClient.isPublic) {
            failAuthentication(
                response, clientNotAllowed,
                detailedMessage = "The authorization flow was started by a public OAuth Client, cannot proceed."
            )
        }

        val allowedClients = _config.allowedOauthClientIds

        if (allowedClients.isNotEmpty() && !allowedClients.contains(oauthClient.id)) {
            val allowedClientsText = allowedClients.joinToString(", ")
            failAuthentication(
                response,
                clientNotAllowed,
                detailedMessage = "OAuth client is not allowed, allowed clients are: $allowedClientsText"
            )
        }
    }

    override fun get(
        requestModel: AccessTokenAuthenticatorRequestModel,
        response: Response
    ): Optional<AuthenticationResult> {
        response.setResponseModel(
            ResponseModel.templateResponseModel(
                emptyMap(), AccessTokenAuthenticatorConstants.TEMPLATE_NAME
            ),
            NOT_FAILURE
        )
        return Optional.empty()
    }

    override fun post(
        requestModel: AccessTokenAuthenticatorRequestModel,
        response: Response
    ): Optional<AuthenticationResult> {
        requestModel as AccessTokenAuthenticatorRequestModel.PostRequestModel

        val subject: String = try {
            val jwtBuilder = JwtConsumerBuilder()
                .setVerificationKey(_config.keyVerification.publicKey)
                .setRequireNotBefore()
                .setRequireExpirationTime()
                .setRequireJwtId()
                .setRequireIssuedAt()
                .setExpectedIssuer(_config.requiredIssuer)

            _config.requiredAudience.ifPresentOrElse({ aud ->
                jwtBuilder.setExpectedAudience(aud)
            }) {
                jwtBuilder.setSkipDefaultAudienceValidation()
            }

            val claims: JwtClaims = jwtBuilder.build()
                .process(requestModel.token)
                .jwtClaims

            validatePurpose(response, claims.getClaimValue("purpose"))
            validateScopes(response, claims.getClaimValue("scope"))
            validateSubjectIsPresent(response, claims.getClaimValue(_config.subjectClaimName))
        } catch (e: GeneralJwtException) {
            _logger.debug("JWT claims error", e)
            throw _exceptionFactory.badRequestException(ErrorCode.INVALID_INPUT, "JWT contains invalid data")
        } catch (e: InvalidJwtSignatureException) {
            _logger.debug("JWT signature error", e)
            failAuthentication(
                response, "Access token signature cannot be recognized",
                detailedMessage = e.errorDetails.joinToString { it.errorMessage ?: it.toString() })
        } catch (e: InvalidJwtException) {
            _logger.debug("JWT is invalid", e)
            failAuthentication(
                response, "Access token is invalid",
                detailedMessage = e.errorDetails.joinToString { it.errorMessage ?: it.toString() })
        }

        return Optional.of(AuthenticationResult(subject))
    }

    private fun validatePurpose(response: Response, purpose: Any?) {
        if (_config.requiredPurpose.isBlank()) {
            _logger.trace("Skipping validation of token purpose claim: {}", purpose)
            return
        }
        if (_config.requiredPurpose != purpose) {
            _logger.debug("Unexpected token purpose: '{}'", purpose)
            failAuthentication(response, "The provided token does not have purpose 'access_token'")
        }
    }

    private fun validateScopes(response: Response, claimValue: Any?) {
        val requiredScopes = _config.requiredScopes.toSet()
        if (requiredScopes.isEmpty()) {
            _logger.trace("Skipping validation of scopes: {}", claimValue)
            return
        }

        val actualScopes = claimValue.asScopeSet(response)

        val missingScopes = requiredScopes - actualScopes
        if (missingScopes.isNotEmpty()) {
            _logger.debug("Token is missing scopes: {}", missingScopes)
            failAuthentication(
                response, "Missing required scope",
                detailedMessage = "Missing scopes: $missingScopes"
            )
        }
    }

    private fun validateSubjectIsPresent(response: Response, sub: Any?): String {
        if (sub == null) {
            _logger.debug("Subject claim [{}] is missing", _config.subjectClaimName)
            failAuthentication(response, "Subject is missing")
        }
        if (sub is String) {
            _logger.trace("Subject claim [{}] is valid", _config.subjectClaimName)
            return sub
        }

        _logger.debug(
            "Subject claim [{}] is not a String, type is {}",
            _config.subjectClaimName,
            sub.javaClass.name
        )

        failAuthentication(response, "Subject is invalid")
    }

    private fun Any?.asScopeSet(response: Response): Set<String> {
        fun throwOnBadType(): Nothing {
            failAuthentication(response, "Scope claim has unexpected type")
        }
        // typically, a single white-separated string is provided
        if (this == null) {
            failAuthentication(response, "No scope claim is present")
        }
        if (this is String) {
            return this.split(' ').toSet()
        }
        if (this is Collection<*>) {
            return this.map {
                it as? String ?: throwOnBadType()
            }.toSet()
        }
        throwOnBadType()
    }

    private fun failAuthentication(response: Response, message: String, detailedMessage: String? = null): Nothing {
        response.addErrorMessage(ErrorMessage.withMessage(message))
        response.setResponseModel(
            ResponseModel.problemResponseModel(ProblemContract.Types.AuthenticationFailed.TYPE, message),
            Response.ResponseModelScope.FAILURE
        )
        throw _exceptionFactory.badRequestException(ErrorCode.INVALID_INPUT, detailedMessage ?: message)
    }

}
