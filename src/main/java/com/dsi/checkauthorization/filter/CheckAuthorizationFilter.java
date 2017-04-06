package com.dsi.checkauthorization.filter;

import com.dsi.checkauthorization.exception.ErrorContext;
import com.dsi.checkauthorization.exception.ErrorMessage;
import com.dsi.checkauthorization.service.impl.CheckAuthService;
import com.dsi.checkauthorization.util.Constants;
import com.dsi.checkauthorization.util.Utility;
import io.jsonwebtoken.Claims;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.io.IOException;

/**
 * Created by sabbir on 6/27/16.
 */

public class CheckAuthorizationFilter implements ContainerRequestFilter {

    @Context
    HttpServletRequest request;

    private static final Logger logger = Logger.getLogger(CheckAuthorizationFilter.class);

    private static final CheckAuthService authService = new CheckAuthService();

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {

        String accessToken = request.getHeader(Constants.AUTHORIZATION);
        String tenantId = request.getHeader(Constants.TENANT_ID);
        String path = requestContext.getUriInfo().getPath();
        String method = requestContext.getRequest().getMethod();

        logger.info("Request path: " + path);
        logger.info("Request method: " + method);

        if(request.getQueryString() != null){
            logger.info("Request path with query: " + path + Constants.QUESTION_SIGN + request.getQueryString());
        }

        MultivaluedMap<String, String> pathParams = requestContext.getUriInfo().getPathParameters();
        if(pathParams != null){
            boolean flag;
            String finalUrl = "";
            String[] parseUrl = path.split("/");

            for (int i=0; i<parseUrl.length; i++){
                flag = true;

                for (String key : pathParams.keySet()) {
                    if (parseUrl[i].equals(pathParams.getFirst(key))) {
                        flag = false;
                        break;
                    }
                }

                if(flag){
                    if(i != 0){
                        finalUrl += "/";
                    }
                    finalUrl += parseUrl[i];
                }
            }
            path = finalUrl;
            logger.info("Final Request path: " + path);
        }

        if(method.equals(Constants.OPTIONS)){
            requestContext.abortWith(Response.status(Response.Status.OK).build());
            return;

        } else {

            if (!path.startsWith(Constants.API_DOCS) && !path.startsWith(Constants.REFERENCE_API)
                    && !path.startsWith(Constants.PHOTO_API)) {

                if (Utility.isNullOrEmpty(accessToken)) {
                    logger.info("AccessToken not defined.");

                    if (!authService.isAllowedApiForPublic(path, method)) {

                        String system = request.getHeader(Constants.SYSTEM);
                        if (Utility.isNullOrEmpty(system)) {
                            ErrorContext errorContext = new ErrorContext(null, "System", "System header not defined.");
                            ErrorMessage errorMessage = new ErrorMessage(Constants.CHECK_AUTHORIZATION_SERVICE_0001,
                                    Constants.CHECK_AUTHORIZATION_SERVICE_0001_DESCRIPTION, errorContext);

                            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).entity(errorMessage).build());
                            return;
                        }

                        if (!Utility.findSystemInfo(system)) {
                            ErrorContext errorContext = new ErrorContext(system, "System", "System info not found: " + system);
                            ErrorMessage errorMessage = new ErrorMessage(Constants.CHECK_AUTHORIZATION_SERVICE_0001,
                                    Constants.CHECK_AUTHORIZATION_SERVICE_0001_DESCRIPTION, errorContext);

                            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).entity(errorMessage).build());
                            return;
                        }

                        if (!authService.isAllowedApiForSystem(path, method)) {
                            ErrorContext errorContext = new ErrorContext(null, "Api", "Api is not allowed by system url: " +
                                    "" + path + " AND method: " + method);
                            ErrorMessage errorMessage = new ErrorMessage(Constants.CHECK_AUTHORIZATION_SERVICE_0001,
                                    Constants.CHECK_AUTHORIZATION_SERVICE_0001_DESCRIPTION, errorContext);

                            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).entity(errorMessage).build());
                            return;
                        }

                    } else {

                        if(path.startsWith(Constants.RESET_API)){
                            if(Utility.isNullOrEmpty(tenantId)){
                                ErrorContext errorContext = new ErrorContext(null, null, "Tenant id not defined.");
                                ErrorMessage errorMessage = new ErrorMessage(Constants.CHECK_AUTHORIZATION_SERVICE_0001,
                                        Constants.CHECK_AUTHORIZATION_SERVICE_0001_DESCRIPTION, errorContext);

                                requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).entity(errorMessage).build());
                                return;
                            }
                            request.setAttribute(Constants.TENANT_ID, tenantId);
                        }
                    }
                } else {

                    String finalAccessToken = Utility.getFinalToken(accessToken);
                    logger.info("AccessToken found: " + finalAccessToken);

                    Claims tokenObj = authService.parseToken(finalAccessToken);
                    if (tokenObj == null) {
                        ErrorContext errorContext = new ErrorContext(null, "AccessToken", "AccessToken parse failed.");
                        ErrorMessage errorMessage = new ErrorMessage(Constants.CHECK_AUTHORIZATION_SERVICE_0001,
                                Constants.CHECK_AUTHORIZATION_SERVICE_0001_DESCRIPTION, errorContext);

                        requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).entity(errorMessage).build());
                        return;
                    }

                    if (!authService.isUserSessionExist(tokenObj.getId(), finalAccessToken)) {
                        ErrorContext errorContext = new ErrorContext(tokenObj.getId(), "UserSession", "UserSession don't exist by userID: "
                                + tokenObj.getId());
                        ErrorMessage errorMessage = new ErrorMessage(Constants.CHECK_AUTHORIZATION_SERVICE_0001,
                                Constants.CHECK_AUTHORIZATION_SERVICE_0001_DESCRIPTION, errorContext);

                        requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).entity(errorMessage).build());
                        return;
                    }

                    if (!authService.isAllowedApiForAuthenticated(path, method) &&
                            !authService.isAllowedApiByUserID(tokenObj.getId(), path, method)) {

                        ErrorContext errorContext = new ErrorContext(tokenObj.getId(), "Api", "Api is not allowed by userID: "
                                + tokenObj.getId());
                        ErrorMessage errorMessage = new ErrorMessage(Constants.CHECK_AUTHORIZATION_SERVICE_0001,
                                Constants.CHECK_AUTHORIZATION_SERVICE_0001_DESCRIPTION, errorContext);

                        requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).entity(errorMessage).build());
                        return;
                    }

                    request.setAttribute(Constants.ACCESS_TOKEN, finalAccessToken);
                    request.setAttribute(Constants.USER_ID, tokenObj.getId());
                    request.setAttribute(Constants.TENANT_NAME, tokenObj.getIssuer());

                    logger.info("Token subject body: " + tokenObj.getSubject());
                    try {
                        JSONObject subjectObj = new JSONObject(tokenObj.getSubject());
                        request.setAttribute(Constants.USER_CONTEXT,
                                subjectObj.has(Constants.USER_CONTEXT) ? subjectObj.getString(Constants.USER_CONTEXT) : null);

                    } catch (JSONException e){
                        ErrorContext errorContext = new ErrorContext(tokenObj.getId(), "TokenParse", e.getMessage());
                        ErrorMessage errorMessage = new ErrorMessage(Constants.CHECK_AUTHORIZATION_SERVICE_0001,
                                Constants.CHECK_AUTHORIZATION_SERVICE_0001_DESCRIPTION, errorContext);

                        requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).entity(errorMessage).build());
                        return;
                    }
                }
            }
        }

        logger.info("Authorized url found.");
    }
}
