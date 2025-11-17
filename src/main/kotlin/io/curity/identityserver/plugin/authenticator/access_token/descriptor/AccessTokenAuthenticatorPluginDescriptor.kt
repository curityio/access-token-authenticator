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

package io.curity.identityserver.plugin.authenticator.access_token.descriptor

import io.curity.identityserver.plugin.authenticator.access_token.authenticate.AccessTokenAuthenticatorRequestHandler
import io.curity.identityserver.plugin.authenticator.access_token.authenticate.HaapiAccessTokenRepresentationFunction
import org.jose4j.jwt.ReservedClaimNames
import se.curity.identityserver.sdk.authentication.AuthenticatorRequestHandler
import se.curity.identityserver.sdk.config.Configuration
import se.curity.identityserver.sdk.config.annotation.DefaultString
import se.curity.identityserver.sdk.config.annotation.Description
import se.curity.identityserver.sdk.config.annotation.SizeConstraint
import se.curity.identityserver.sdk.haapi.RepresentationFunction
import se.curity.identityserver.sdk.plugin.descriptor.AuthenticatorPluginDescriptor
import se.curity.identityserver.sdk.service.RequestingOAuthClient
import se.curity.identityserver.sdk.service.crypto.AsymmetricSignatureVerificationCryptoStore
import java.util.Optional

object AccessTokenAuthenticatorConstants {
    const val PLUGIN_TYPE = "access-token"
    const val TEMPLATE_NAME = "authenticate/start"
}

/**
 * Plugin configuration object.
 */
interface AccessTokenAuthenticatorConfig : Configuration {
    @get:Description("The expected token issuer.")
    @get:SizeConstraint(min = 2, max = 1024)
    val requiredIssuer: String

    @get:Description("The expected token audience.")
    val requiredAudience: Optional<@SizeConstraint(min = 2, max = 128) String>

    @get:Description("The required scopes, if any.")
    val requiredScopes: List<String>

    @get:DefaultString("access_token")
    @get:Description(
        "The required value of the 'purpose' claim. " +
                "If set to a blank String, it will not be validated. " +
                "By default, the value 'access_token' is used."
    )
    val requiredPurpose: String

    @get:Description("The name of the claim to extract the subject from. By default, 'sub' is used.")
    @get:DefaultString(ReservedClaimNames.SUBJECT)
    @get:SizeConstraint(min = 1, max = 64)
    val subjectClaimName: String

    @get:Description("The IDs of the allowed OAuth clients. If empty, any confidential OAuth client will be allowed.")
    val allowedOauthClientIds: List<@SizeConstraint(min = 1, max = 128) String>

    @get:Description("The asymmetric key to use to verify the token signature.")
    val keyVerification: AsymmetricSignatureVerificationCryptoStore

    /**
     * Service to obtain the requesting OAuth client.
     *
     * This authenticator will allow only confidential clients. Consequently, the client must be present.
     *
     * Finally, the OAuth client must be in [allowedOauthClientIds] if that List is not empty.
     */
    val requestingOAuthClient: RequestingOAuthClient
}

/**
 * Access token Authenticator Plugin descriptor.
 *
 * This authenticator is designed to only support authentication via HAAPI since end users are not expected
 * to get direct access to their access tokens.
 *
 * If you want this authenticator to also work in a browser, you must provide your own Velocity template
 * at `templates/authenticator/access_token/authenticate/start.vm`.
 */
class AccessTokenAuthenticatorPluginDescriptor : AuthenticatorPluginDescriptor<AccessTokenAuthenticatorConfig> {

    override fun getPluginImplementationType(): String = AccessTokenAuthenticatorConstants.PLUGIN_TYPE

    override fun getConfigurationType(): Class<out AccessTokenAuthenticatorConfig> =
        AccessTokenAuthenticatorConfig::class.java

    override fun getAuthenticationRequestHandlerTypes(): Map<String, Class<out AuthenticatorRequestHandler<*>>> =
        mapOf("index" to AccessTokenAuthenticatorRequestHandler::class.java)

    override fun getRepresentationFunctions(): Map<String, Class<out RepresentationFunction>> {
        return mapOf(
            AccessTokenAuthenticatorConstants.TEMPLATE_NAME to HaapiAccessTokenRepresentationFunction::class.java,
        )
    }
}
