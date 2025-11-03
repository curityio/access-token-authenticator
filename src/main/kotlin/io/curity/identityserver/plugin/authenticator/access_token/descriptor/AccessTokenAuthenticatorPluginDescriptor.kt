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
import se.curity.identityserver.sdk.authentication.AuthenticatorRequestHandler
import se.curity.identityserver.sdk.config.Configuration
import se.curity.identityserver.sdk.haapi.RepresentationFunction
import se.curity.identityserver.sdk.plugin.descriptor.AuthenticatorPluginDescriptor
import se.curity.identityserver.sdk.service.crypto.AsymmetricSignatureVerificationCryptoStore

/**
 * Plugin configuration object.
 */
interface AccessTokenAuthenticatorConfig : Configuration {
    val tokenIssuer: String
    val tokenAudience: String
    val keyVerification: AsymmetricSignatureVerificationCryptoStore
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

    override fun getPluginImplementationType(): String = "access_token"

    override fun getConfigurationType(): Class<out AccessTokenAuthenticatorConfig> =
        AccessTokenAuthenticatorConfig::class.java

    override fun getAuthenticationRequestHandlerTypes(): Map<String, Class<out AuthenticatorRequestHandler<*>>> =
        mapOf("index" to AccessTokenAuthenticatorRequestHandler::class.java)

    override fun getRepresentationFunctions(): Map<String, Class<out RepresentationFunction>> {
        return mapOf("authenticate/start" to HaapiAccessTokenRepresentationFunction::class.java)
    }
}
