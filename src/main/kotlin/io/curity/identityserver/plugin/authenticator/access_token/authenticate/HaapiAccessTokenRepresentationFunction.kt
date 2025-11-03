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
import se.curity.identityserver.sdk.haapi.*
import se.curity.identityserver.sdk.http.HttpMethod
import se.curity.identityserver.sdk.http.MediaType
import se.curity.identityserver.sdk.service.authentication.AuthenticatorInformationProvider
import se.curity.identityserver.sdk.web.Representation
import java.util.function.Consumer

/**
 * Support for HAAPI.
 *
 * This is a simple representation that expects the client to provide an access_token it obtained by
 * undefined means.
 *
 * The token will be validated by [AccessTokenAuthenticatorRequestHandler], and if approved, the user will
 * be authenticated.
 */
class HaapiAccessTokenRepresentationFunction(
    private val _config: AccessTokenAuthenticatorConfig,
    private val _helper: AuthenticatorInformationProvider,
) : RepresentationFunction {
    override fun apply(model: RepresentationModel, factory: RepresentationFactory): Representation {
        return factory.newAuthenticationStep { builder ->
            builder.addFormAction(
                ActionKind.LOGIN,
                _helper.fullyQualifiedAuthenticationUri,
                HttpMethod.POST,
                MediaType.X_WWW_FORM_URLENCODED,
                Message.ofLiteral("Login"),
                Message.ofLiteral("login-form"),
                Consumer { fields: FormActionConfigurator ->
                    fields.addTextField(
                        "token",
                        Message.ofLiteral("Token"),
                    )
                }
            )
        }
    }
}