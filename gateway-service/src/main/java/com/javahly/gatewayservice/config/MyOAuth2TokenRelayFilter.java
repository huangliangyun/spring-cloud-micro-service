package com.javahly.gatewayservice.config;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import java.util.HashMap;
import java.util.Map;

import org.springframework.cloud.security.oauth2.proxy.ProxyAuthenticationProperties;
import org.springframework.cloud.security.oauth2.proxy.ProxyAuthenticationProperties.Route;
import org.springframework.cloud.security.oauth2.proxy.ProxyAuthenticationProperties.Route.Scheme;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2RestOperations;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.stereotype.Component;
import javax.servlet.http.HttpServletResponse;

/**
 * @author :hly
 * @github :https://github.com/huangliangyun
 * @blog :http://www.javahly.com/
 * @CSDN :blog.csdn.net/Sirius_hly
 * @date :2019/6/2
 * @QQ :1136513099
 * @desc :
 */
@Component("oAuth2TokenRelayFilter")
public class MyOAuth2TokenRelayFilter extends ZuulFilter {

    private static final String ACCESS_TOKEN = "ACCESS_TOKEN";
    private static final String TOKEN_TYPE = "TOKEN_TYPE";
    private Map<String, Route> routes = new HashMap<String, Route>();

    private OAuth2RestOperations restTemplate;

    public MyOAuth2TokenRelayFilter(ProxyAuthenticationProperties properties) {
        this.routes = properties.getRoutes();
    }

    public void setRestTemplate(OAuth2RestOperations restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public int filterOrder() {
        return 10;
    }

    @Override
    public String filterType() {
        return "pre";
    }

    @Override
    public boolean shouldFilter() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof OAuth2Authentication) {
            Object details = auth.getDetails();
            if (details instanceof OAuth2AuthenticationDetails) {
                OAuth2AuthenticationDetails oauth = (OAuth2AuthenticationDetails) details;
                RequestContext ctx = RequestContext.getCurrentContext();
                if (ctx.containsKey("proxy")) {
                    String id = (String) ctx.get("proxy");
                    if (routes.containsKey(id)) {
                        if (!Route.Scheme.OAUTH2.matches(routes.get(id).getScheme())) {
                            return false;
                        }
                    }
                }
                ctx.set(ACCESS_TOKEN, oauth.getTokenValue());
                ctx.set(TOKEN_TYPE, oauth.getTokenType()==null ? "Bearer" : oauth.getTokenType());
                return true;
            }
        }
        return false;
    }

    @Override
    public Object run() {
        RequestContext ctx = RequestContext.getCurrentContext();
        ctx.addZuulRequestHeader("authorization", ctx.get(TOKEN_TYPE) + " " + getAccessToken(ctx));
        return null;
    }

    private String getAccessToken(RequestContext ctx) {
        String value = (String) ctx.get(ACCESS_TOKEN);
        if ( value != null && !value.isEmpty()){
            return value ;
        }
        if (restTemplate != null) {
            // In case it needs to be refreshed
            OAuth2Authentication auth = (OAuth2Authentication) SecurityContextHolder
                    .getContext().getAuthentication();
            if (restTemplate.getResource().getClientId()
                    .equals(auth.getOAuth2Request().getClientId())) {
                try {
                    value = restTemplate.getAccessToken().getValue();
                }
                catch (Exception e) {
                    // Quite possibly a UserRedirectRequiredException, but the caller
                    // probably doesn't know how to handle it, otherwise they wouldn't be
                    // using this filter, so we rethrow as an authentication exception
                    ctx.set("error.status_code", HttpServletResponse.SC_UNAUTHORIZED);
                    throw new BadCredentialsException("Cannot obtain valid access token");
                }
            }
        }
        return value;
    }

}