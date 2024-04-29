/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License
 */

package org.wso2.carbon.identity.oauth2.token.handler.clientauth.jwt;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.base.IdentityConstants;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.oauth.common.OAuth2ErrorCodes;
import org.wso2.carbon.identity.oauth2.bean.OAuthClientAuthnContext;
import org.wso2.carbon.identity.oauth2.client.authentication.AbstractOAuthClientAuthenticator;
import org.wso2.carbon.identity.oauth2.client.authentication.OAuthClientAuthnException;
import org.wso2.carbon.identity.oauth2.token.handler.clientauth.jwt.validator.JWTValidator;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.isNotEmpty;
import static org.wso2.carbon.identity.oauth2.token.handler.clientauth.jwt.Constants.AUDIENCE_CLAIM;
import static org.wso2.carbon.identity.oauth2.token.handler.clientauth.jwt.Constants.DEFAULT_ENABLE_JTI_CACHE;
import static org.wso2.carbon.identity.oauth2.token.handler.clientauth.jwt.Constants.DEFAULT_AUDIENCE;
import static org.wso2.carbon.identity.oauth2.token.handler.clientauth.jwt.Constants.DEFAULT_VALIDITY_PERIOD_IN_MINUTES;
import static org.wso2.carbon.identity.oauth2.token.handler.clientauth.jwt.Constants.EXPIRATION_TIME_CLAIM;
import static org.wso2.carbon.identity.oauth2.token.handler.clientauth.jwt.Constants.ISSUER_CLAIM;
import static org.wso2.carbon.identity.oauth2.token.handler.clientauth.jwt.Constants.JWT_ID_CLAIM;
import static org.wso2.carbon.identity.oauth2.token.handler.clientauth.jwt.Constants.OAUTH_JWT_ASSERTION;
import static org.wso2.carbon.identity.oauth2.token.handler.clientauth.jwt.Constants.OAUTH_JWT_ASSERTION_TYPE;
import static org.wso2.carbon.identity.oauth2.token.handler.clientauth.jwt.Constants.OAUTH_JWT_BEARER_GRANT_TYPE;
import static org.wso2.carbon.identity.oauth2.token.handler.clientauth.jwt.Constants.PREVENT_TOKEN_REUSE;
import static org.wso2.carbon.identity.oauth2.token.handler.clientauth.jwt.Constants.REJECT_BEFORE_IN_MINUTES;
import static org.wso2.carbon.identity.oauth2.token.handler.clientauth.jwt.Constants.PRIVATE_KEY_JWT;
import static org.wso2.carbon.identity.oauth2.token.handler.clientauth.jwt.Constants.SUBJECT_CLAIM;
import static org.wso2.carbon.identity.oauth2.token.handler.clientauth.jwt.Constants.TOKEN_ENDPOINT_ALIAS;

/**
 * Client Authentication handler to implement oidc private_key_jwt client authentication specDEFAULT_TOKEN_EP_ALIAS
 * http://openid.net/specs/openid-connect-core-1_0.html#ClientAuthentication.
 */
public class PrivateKeyJWTClientAuthenticator extends AbstractOAuthClientAuthenticator {

    private static final Log log = LogFactory.getLog(PrivateKeyJWTClientAuthenticator.class);
    private JWTValidator jwtValidator;

    public PrivateKeyJWTClientAuthenticator() {

        int rejectBeforePeriod = DEFAULT_VALIDITY_PERIOD_IN_MINUTES;
        boolean preventTokenReuse = true;
        String tokenEPAlias = DEFAULT_AUDIENCE;
        try {
            if (isNotEmpty(properties.getProperty(TOKEN_ENDPOINT_ALIAS))) {
                tokenEPAlias = properties.getProperty(TOKEN_ENDPOINT_ALIAS);
            }
            if (isNotEmpty(properties.getProperty(PREVENT_TOKEN_REUSE))) {
                preventTokenReuse = Boolean.parseBoolean(properties.getProperty(PREVENT_TOKEN_REUSE));
            }
            if (isNotEmpty(properties.getProperty(REJECT_BEFORE_IN_MINUTES))) {
                rejectBeforePeriod = Integer.parseInt(properties.getProperty(REJECT_BEFORE_IN_MINUTES));
            }
            jwtValidator = createJWTValidator(tokenEPAlias, preventTokenReuse, rejectBeforePeriod);
        } catch (NumberFormatException e) {
            log.warn("Invalid PrivateKeyJWT Validity period found in the configuration. Using default value: " +
                    rejectBeforePeriod);
        }
    }

    /**
     * To check whether the authentication is successful.
     *
     * @param httpServletRequest      http servelet request
     * @param bodyParameters          map of request body params
     * @param oAuthClientAuthnContext oAuthClientAuthnContext
     * @return true if the authentication is successful.
     * @throws OAuthClientAuthnException
     */
    @Override
    public boolean authenticateClient(HttpServletRequest httpServletRequest, Map<String, List> bodyParameters,
                                      OAuthClientAuthnContext oAuthClientAuthnContext) throws OAuthClientAuthnException {

        return jwtValidator.isValidAssertion(getSignedJWT(bodyParameters, oAuthClientAuthnContext),
                isBackchannelCall(httpServletRequest));
    }

    /**
     * Returns whether the incoming request can be handled by the particular authenticator.
     *
     * @param httpServletRequest      http servelet request
     * @param bodyParameters          map of request body params
     * @param oAuthClientAuthnContext oAuthClientAuthnContext
     * @return true if the incoming request can be handled.
     */
    @Override
    public boolean canAuthenticate(HttpServletRequest httpServletRequest, Map<String, List> bodyParameters,
                                   OAuthClientAuthnContext oAuthClientAuthnContext) {

        String oauthJWTAssertionType = getBodyParameters(bodyParameters).get(OAUTH_JWT_ASSERTION_TYPE);
        String oauthJWTAssertion = getBodyParameters(bodyParameters).get(OAUTH_JWT_ASSERTION);
        return isValidJWTClientAssertionRequest(oauthJWTAssertionType, oauthJWTAssertion);
    }

    /**
     * Retrievs the client ID which is extracted from the JWT.
     *
     * @param httpServletRequest
     * @param bodyParameters
     * @param oAuthClientAuthnContext
     * @return jwt 'sub' value as the client id
     * @throws OAuthClientAuthnException
     */
    @Override
    public String getClientId(HttpServletRequest httpServletRequest, Map<String, List> bodyParameters,
                              OAuthClientAuthnContext oAuthClientAuthnContext) throws OAuthClientAuthnException {

        SignedJWT signedJWT = getSignedJWT(bodyParameters, oAuthClientAuthnContext);
        JWTClaimsSet claimsSet = jwtValidator.getClaimSet(signedJWT);
        return jwtValidator.resolveSubject(claimsSet);
    }

    protected void setJwtValidator(JWTValidator jwtValidator) {

        this.jwtValidator = jwtValidator;
    }

    private SignedJWT getSignedJWT(Map<String, List> bodyParameters, OAuthClientAuthnContext oAuthClientAuthnContext)
            throws OAuthClientAuthnException {

        Object signedJWTFromContext = oAuthClientAuthnContext.getParameter(PRIVATE_KEY_JWT);
        if (signedJWTFromContext != null) {
            return (SignedJWT) signedJWTFromContext;
        }
        String assertion = getBodyParameters(bodyParameters).get(OAUTH_JWT_ASSERTION);
        String errorMessage = "No Valid Assertion was found for " + Constants.OAUTH_JWT_BEARER_GRANT_TYPE;
        SignedJWT signedJWT;
        if (isEmpty(assertion)) {
            throw new OAuthClientAuthnException(errorMessage, OAuth2ErrorCodes.INVALID_REQUEST);
        }
        try {
            signedJWT = SignedJWT.parse(assertion);
        } catch (ParseException e) {
            if (log.isDebugEnabled()) {
                log.debug(e.getMessage());
            }
            throw new OAuthClientAuthnException("Error while parsing the JWT.", OAuth2ErrorCodes.INVALID_REQUEST);
        }
        if (signedJWT == null) {
            throw new OAuthClientAuthnException(errorMessage, OAuth2ErrorCodes.INVALID_REQUEST);
        }
        oAuthClientAuthnContext.addParameter(PRIVATE_KEY_JWT, signedJWT);
        return signedJWT;
    }

    private boolean isValidJWTClientAssertionRequest(String clientAssertionType, String clientAssertion) {

        if (log.isDebugEnabled()) {
            log.debug("Authenticate Requested with clientAssertionType : " + clientAssertionType);
            if (IdentityUtil.isTokenLoggable(IdentityConstants.IdentityTokens.ACCESS_TOKEN)) {
                log.debug("Authenticate Requested with clientAssertion : " + clientAssertion);
            }
        }
        return OAUTH_JWT_BEARER_GRANT_TYPE.equals(clientAssertionType) && isNotEmpty(clientAssertion);
    }

    private JWTValidator createJWTValidator(String validAudience, boolean preventTokenReuse,
                                            int rejectBefore) {

        return new JWTValidator(preventTokenReuse, validAudience, rejectBefore, null,
                populateMandatoryClaims(), DEFAULT_ENABLE_JTI_CACHE);
    }

    private List<String> populateMandatoryClaims() {

        List<String> mandatoryClaims = new ArrayList<>();
        mandatoryClaims.add(ISSUER_CLAIM);
        mandatoryClaims.add(SUBJECT_CLAIM);
        mandatoryClaims.add(AUDIENCE_CLAIM);
        mandatoryClaims.add(EXPIRATION_TIME_CLAIM);
        mandatoryClaims.add(JWT_ID_CLAIM);
        return mandatoryClaims;
    }

    /**
     * Check if the request is CIBA specific
     * @param httpServletRequest Request
     * @return Whether the call is CIBA specific
     */
    private boolean isBackchannelCall(HttpServletRequest httpServletRequest) {
        // Although CIBA call uses jwt-bearer grant type, special audience values should be set
        return Constants.OAUTH2_CIBA_EP.equals(httpServletRequest.getContextPath() +
                httpServletRequest.getPathInfo());
    }
}
