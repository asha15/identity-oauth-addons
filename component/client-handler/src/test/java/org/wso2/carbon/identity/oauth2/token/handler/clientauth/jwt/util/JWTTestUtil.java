/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.identity.oauth2.token.handler.clientauth.jwt.util;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.apache.commons.lang.StringUtils;
import org.wso2.carbon.identity.oauth2.IdentityOAuth2Exception;
import org.wso2.carbon.identity.oauth2.token.handler.clientauth.jwt.Constants;
import org.wso2.carbon.identity.oauth2.token.handler.clientauth.jwt.validator.JWTValidator;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Properties;

public class JWTTestUtil {

    public static String buildJWT(String issuer, String subject, String jti, String audience, String algorythm,
                                  Key privateKey, long notBeforeMillis)
            throws IdentityOAuth2Exception {

        long lifetimeInMillis = 3600 * 1000;
        long curTimeInMillis = Calendar.getInstance().getTimeInMillis();

        // Set claims to jwt token.
        JWTClaimsSet.Builder jwtClaimsSetBuilder = new JWTClaimsSet.Builder();
        jwtClaimsSetBuilder.issuer(issuer);
        jwtClaimsSetBuilder.subject(subject);
        jwtClaimsSetBuilder.audience(Arrays.asList(audience));
        jwtClaimsSetBuilder.jwtID(jti);
        jwtClaimsSetBuilder.expirationTime(new Date(curTimeInMillis + lifetimeInMillis));
        jwtClaimsSetBuilder.issueTime(new Date(curTimeInMillis));

        if (notBeforeMillis > 0) {
            jwtClaimsSetBuilder.notBeforeTime(new Date(curTimeInMillis + notBeforeMillis));
        }
        JWTClaimsSet jwtClaimsSet = jwtClaimsSetBuilder.build();
        if (JWSAlgorithm.NONE.getName().equals(algorythm)) {
            return new PlainJWT(jwtClaimsSet).serialize();
        }

        return signJWTWithRSA(jwtClaimsSet, privateKey);
    }

    public static String buildJWT(String issuer, String subject, String jti, String audience, String algorythm,
                                  Key privateKey, long notBeforeMillis, long lifetimeInMillis, long issuedTime)
            throws IdentityOAuth2Exception {

        long curTimeInMillis = Calendar.getInstance().getTimeInMillis();
        if (issuedTime < 0) {
            issuedTime = curTimeInMillis;
        }
        if (lifetimeInMillis <= 0) {
            lifetimeInMillis = 3600 * 1000;
        }
        // Set claims to jwt token.
        JWTClaimsSet.Builder jwtClaimsSetBuilder = new JWTClaimsSet.Builder();
        jwtClaimsSetBuilder.issuer(issuer);
        jwtClaimsSetBuilder.subject(subject);
        jwtClaimsSetBuilder.audience(Arrays.asList(audience));
        jwtClaimsSetBuilder.jwtID(jti);
        jwtClaimsSetBuilder.expirationTime(new Date(issuedTime + lifetimeInMillis));
        jwtClaimsSetBuilder.issueTime(new Date(issuedTime));
        jwtClaimsSetBuilder.notBeforeTime(new Date(notBeforeMillis));

        if (notBeforeMillis > 0) {
            jwtClaimsSetBuilder.notBeforeTime(new Date(issuedTime + notBeforeMillis));
        }
        JWTClaimsSet jwtClaimsSet = jwtClaimsSetBuilder.build();
        if (JWSAlgorithm.NONE.getName().equals(algorythm)) {
            return new PlainJWT(jwtClaimsSet).serialize();
        }

        return signJWTWithRSA(jwtClaimsSet, privateKey);
    }

    public static String buildExpiredJWT(String issuer, String subject, String jti, String audience, String algorythm,
                                         Key privateKey, long notBeforeMillis, long lifetimeInMillis, long issuedTime)
            throws IdentityOAuth2Exception {

        long curTimeInMillis = Calendar.getInstance().getTimeInMillis();
        if (issuedTime < 0) {
            issuedTime = curTimeInMillis;
        }
        if (lifetimeInMillis <= 0) {
            lifetimeInMillis = 3600 * 1000;
        }
        // Set claims to jwt token.
        JWTClaimsSet.Builder jwtClaimsSetBuilder = new JWTClaimsSet.Builder();
        jwtClaimsSetBuilder.issuer(issuer);
        jwtClaimsSetBuilder.subject(subject);
        jwtClaimsSetBuilder.audience(Arrays.asList(audience));
        jwtClaimsSetBuilder.jwtID(jti);
        jwtClaimsSetBuilder.expirationTime(new Date(issuedTime - lifetimeInMillis));
        jwtClaimsSetBuilder.issueTime(new Date(issuedTime));
        jwtClaimsSetBuilder.notBeforeTime(new Date(notBeforeMillis));

        if (notBeforeMillis > 0) {
            jwtClaimsSetBuilder.notBeforeTime(new Date(issuedTime + notBeforeMillis));
        }
        JWTClaimsSet jwtClaimsSet = jwtClaimsSetBuilder.build();
        if (JWSAlgorithm.NONE.getName().equals(algorythm)) {
            return new PlainJWT(jwtClaimsSet).serialize();
        }

        return signJWTWithRSA(jwtClaimsSet, privateKey);
    }

    /**
     * sign JWT token from RSA algorithm
     *
     * @param jwtClaimsSet contains JWT body
     * @param privateKey
     * @return signed JWT token
     * @throws IdentityOAuth2Exception
     */
    public static String signJWTWithRSA(JWTClaimsSet jwtClaimsSet, Key privateKey)
            throws IdentityOAuth2Exception {

        try {
            JWSSigner signer = new RSASSASigner((RSAPrivateKey) privateKey);
            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), jwtClaimsSet);
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new IdentityOAuth2Exception("Error occurred while signing JWT", e);
        }
    }

    /**
     * Read Keystore from the file identified by given keystorename, password
     *
     * @param keystoreName
     * @param password
     * @param home
     * @return
     * @throws Exception
     */
    public static KeyStore getKeyStoreFromFile(String keystoreName, String password,
                                               String home) throws Exception {

        Path tenantKeystorePath = Paths.get(home, "repository",
                "resources", "security", keystoreName);
        FileInputStream file = new FileInputStream(tenantKeystorePath.toString());
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        keystore.load(file, password.toCharArray());
        return keystore;
    }

    /**
     * Create and return a JWTValidator instance with given properties
     *
     * @param properties
     * @return
     */
    public static JWTValidator getJWTValidator(Properties properties) {

        int rejectBeforePeriod;
        boolean cacheUsedJTI = true;
        List<String> validAudiences = new ArrayList<>();
        String validIssuer = null;
        boolean preventTokenReuse = true;
        List<String> mandatoryClaims = new ArrayList<>();
        try {

            String rejectBeforePeriodConfigVal = properties.getProperty(Constants.REJECT_BEFORE_IN_MINUTES);
            if (StringUtils.isNotEmpty(rejectBeforePeriodConfigVal)) {
                rejectBeforePeriod = Integer.parseInt(rejectBeforePeriodConfigVal);
            } else {
                rejectBeforePeriod = Constants.DEFAULT_VALIDITY_PERIOD_IN_MINUTES;
            }

            String cacheUsedJTIConfigVal = properties.getProperty("EnableCacheForJTI");
            if (StringUtils.isNotEmpty(cacheUsedJTIConfigVal)) {
                cacheUsedJTI = Boolean.parseBoolean(cacheUsedJTIConfigVal);
            } else {
                cacheUsedJTI = Constants.DEFAULT_ENABLE_JTI_CACHE;
            }

            String validAudienceConfigVal1 = properties.getProperty("ValidAudience1");
            if (StringUtils.isNotEmpty(validAudienceConfigVal1)) {
                validAudiences.add(validAudienceConfigVal1);
            }

            String validAudienceConfigVal2 = properties.getProperty("ValidAudience2");
            if (StringUtils.isNotEmpty(validAudienceConfigVal2)) {
                validAudiences.add(validAudienceConfigVal2);
            }

            String validIssuerConfigVal = properties.getProperty("ValidIssuer");
            if (StringUtils.isNotEmpty(validIssuerConfigVal)) {
                validIssuer = validIssuerConfigVal;
            } else {
                validIssuer = null;
            }

            String preventTokenReuseProperty = properties.getProperty("PreventTokenReuse");
            if (StringUtils.isNotEmpty(preventTokenReuseProperty)) {
                preventTokenReuse = Boolean.parseBoolean(preventTokenReuseProperty);
            }

            String mandatory = properties.getProperty("mandatory");
            if (StringUtils.isNotEmpty(mandatory)) {
                mandatoryClaims.add(mandatory);
            }

        } catch (NumberFormatException e) {
            rejectBeforePeriod = Constants.DEFAULT_VALIDITY_PERIOD_IN_MINUTES;
        }

        if (validAudiences.isEmpty()) {
            return new JWTValidator(preventTokenReuse, (String) null, rejectBeforePeriod, validIssuer,
                    mandatoryClaims, cacheUsedJTI);
        } else if (validAudiences.size() == 1) {
            return new JWTValidator(preventTokenReuse, validAudiences.get(0), rejectBeforePeriod, validIssuer,
                    mandatoryClaims, cacheUsedJTI);
        } else {
            return new JWTValidator(preventTokenReuse, validAudiences, rejectBeforePeriod, validIssuer,
                    mandatoryClaims, cacheUsedJTI);
        }
    }
}
