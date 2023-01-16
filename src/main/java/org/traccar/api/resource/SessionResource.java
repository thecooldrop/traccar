/*
 * Copyright 2015 - 2022 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.api.resource;

import org.traccar.api.BaseResource;
import org.traccar.api.security.LoginService;
import org.traccar.api.signature.TokenManager;
import org.traccar.helper.DataConverter;
import org.traccar.helper.LogAction;
import org.traccar.helper.ServletHelper;
import org.traccar.model.User;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Date;

@Path("session")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
public class SessionResource extends BaseResource {

    public static final String USER_ID_KEY = "userId";
    public static final String USER_COOKIE_KEY = "user";
    public static final String PASS_COOKIE_KEY = "password";

    @Inject
    private LoginService loginService;

    @Inject
    private TokenManager tokenManager;

    @Context
    private HttpServletRequest request;

    @PermitAll
    @GET
    public User get(@QueryParam("token") String token) throws StorageException, IOException, GeneralSecurityException {

        if (token != null) {
            User user = loginService.login(token);
            if (user != null) {
                request.getSession().setAttribute(USER_ID_KEY, user.getId());
                LogAction.login(user.getId(), ServletHelper.retrieveRemoteAddress(request));
                return user;
            }
        }

        Long userId = (Long) request.getSession().getAttribute(USER_ID_KEY);
        if (userId == null) {

            Cookie[] cookies = request.getCookies();
            String email = null, password = null;
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (cookie.getName().equals(USER_COOKIE_KEY)) {
                        byte[] emailBytes = DataConverter.parseBase64(
                                URLDecoder.decode(cookie.getValue(), StandardCharsets.US_ASCII));
                        email = new String(emailBytes, StandardCharsets.UTF_8);
                    } else if (cookie.getName().equals(PASS_COOKIE_KEY)) {
                        byte[] passwordBytes = DataConverter.parseBase64(
                                URLDecoder.decode(cookie.getValue(), StandardCharsets.US_ASCII));
                        password = new String(passwordBytes, StandardCharsets.UTF_8);
                    }
                }
            }
            if (email != null && password != null) {
                User user = loginService.login(email, password);
                if (user != null) {
                    request.getSession().setAttribute(USER_ID_KEY, user.getId());
                    LogAction.login(user.getId(), ServletHelper.retrieveRemoteAddress(request));
                    return user;
                }
            }

        } else {

            User user = permissionsService.getUser(userId);
            if (user != null) {
                return user;
            }

        }

        throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Path("{id}")
    @GET
    public User get(@PathParam("id") long userId) throws StorageException {
        permissionsService.checkAdmin(getUserId());
        User user = storage.getObject(User.class, new Request(
                new Columns.All(), new Condition.Equals("id", userId)));
        request.getSession().setAttribute(USER_ID_KEY, user.getId());
        LogAction.login(user.getId(), ServletHelper.retrieveRemoteAddress(request));
        return user;
    }

    @PermitAll
    @POST
    public User add(
            @FormParam("email") String email, @FormParam("password") String password) throws StorageException {
        User user = loginService.login(email, password);
        if (user != null) {
            request.getSession().setAttribute(USER_ID_KEY, user.getId());
            LogAction.login(user.getId(), ServletHelper.retrieveRemoteAddress(request));
            return user;
        } else {
            LogAction.failedLogin(ServletHelper.retrieveRemoteAddress(request));
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }
    }

    @DELETE
    public Response remove() {
        LogAction.logout(getUserId(), ServletHelper.retrieveRemoteAddress(request));
        request.getSession().removeAttribute(USER_ID_KEY);
        return Response.noContent().build();
    }

    @Path("token")
    @POST
    public String requestToken(
            @FormParam("expiration") Date expiration) throws StorageException, GeneralSecurityException, IOException {
        return tokenManager.generateToken(getUserId(), expiration);
    }

}
