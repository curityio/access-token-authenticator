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
import jakarta.validation.constraints.NotEmpty
import org.jose4j.jwt.GeneralJwtException
import org.jose4j.jwt.JwtClaims
import org.jose4j.jwt.consumer.InvalidJwtException
import org.jose4j.jwt.consumer.InvalidJwtSignatureException
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.slf4j.LoggerFactory
import se.curity.identityserver.sdk.authentication.AuthenticationResult
import se.curity.identityserver.sdk.authentication.AuthenticatorRequestHandler
import se.curity.identityserver.sdk.authorization.AuthorizationErrorMessage
import se.curity.identityserver.sdk.errors.ErrorCode
import se.curity.identityserver.sdk.service.ExceptionFactory
import se.curity.identityserver.sdk.web.Request
import se.curity.identityserver.sdk.web.Response
import se.curity.identityserver.sdk.web.Response.ResponseModelScope.NOT_FAILURE
import se.curity.identityserver.sdk.web.ResponseModel
import java.util.*

/**
 * Request model for the access_token authenticator request handler.
 *
 * The only requirement is that on POST requests, a "token" is provided as a form parameter.
 */
sealed interface AccessTokenAuthenticatorRequestModel {
    object GetRequestModel : AccessTokenAuthenticatorRequestModel
    class PostRequestModel(
        @get:NotEmpty("access token is mandatory")
        val accessToken: String?,
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
    ): AccessTokenAuthenticatorRequestModel =
        if (request.isGetRequest) AccessTokenAuthenticatorRequestModel.forGet()
        else AccessTokenAuthenticatorRequestModel.forPost(request)

    override fun get(
        requestModel: AccessTokenAuthenticatorRequestModel,
        response: Response
    ): Optional<AuthenticationResult> {
        response.setResponseModel(
            ResponseModel.templateResponseModel(
                emptyMap(), "authenticate/start"
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
            val claims: JwtClaims = JwtConsumerBuilder()
                .setVerificationKey(_config.keyVerification.publicKey)
                .setRequireNotBefore()
                .setRequireExpirationTime()
                .setRequireJwtId()
                .setRequireIssuedAt()
                .setExpectedAudience(_config.tokenAudience)
                .setExpectedIssuer(_config.tokenIssuer)
                .build()
                .process(requestModel.accessToken)
                .jwtClaims

            val purpose: Any? = claims.getClaimValue("purpose")
            if ("access_token" != purpose) {
                _logger.debug("Unexpected token purpose: '{}'", purpose)
                throw _exceptionFactory.authorizationException(
                    setOf(AuthorizationErrorMessage.of("The provided token does not have purpose 'access_token'"))
                )
            }

            claims.subject
        } catch (e: GeneralJwtException) {
            _logger.debug("JWT claims error", e)
            throw _exceptionFactory.badRequestException(ErrorCode.INVALID_INPUT, "JWT contains invalid data")
        } catch (e: InvalidJwtSignatureException) {
            _logger.debug("JWT signature error", e)
            throw _exceptionFactory.authorizationException(
                setOf(
                    AuthorizationErrorMessage.of("Access token signature cannot be recognized")
                )
            )
        } catch (e: InvalidJwtException) {
            _logger.debug("JWT is invalid", e)
            throw _exceptionFactory.authorizationException(
                setOf(
                    AuthorizationErrorMessage.of("Access token is invalid")
                )
            )
        }

        return Optional.of(AuthenticationResult(subject))
    }
}
