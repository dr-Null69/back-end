package com.example.anml.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CurrentUserArgumentResolverTest {

    private final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void supportsParameter_returnsTrueForAnnotatedCurrentUserInfoAndFalseOtherwise() throws NoSuchMethodException {
        MethodParameter withAnnotation = methodParam("withAnnotation", CurrentUserInfo.class);
        MethodParameter withoutAnnotation = methodParam("withoutAnnotation", CurrentUserInfo.class);
        MethodParameter plainParameter = methodParam("plainParameter", String.class);
        MethodParameter wrongType = methodParam("wrongType", String.class);

        assertTrue(resolver.supportsParameter(withAnnotation));
        assertFalse(resolver.supportsParameter(withoutAnnotation));
        assertFalse(resolver.supportsParameter(plainParameter));
        assertFalse(resolver.supportsParameter(wrongType));
    }

    @Test
    void resolveArgument_whenAuthenticated_returnsCurrentUserInfoWithUsernameAndSortedRoles() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin1",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        MethodParameter param = methodParam("withAnnotation", CurrentUserInfo.class);
        Object resolved = resolver.resolveArgument(
                param, null, new ServletWebRequest(new MockHttpServletRequest()), null);

        assertInstanceOf(CurrentUserInfo.class, resolved);
        CurrentUserInfo userInfo = (CurrentUserInfo) resolved;
        assertTrue(userInfo.authenticated());
        assertEquals("admin1", userInfo.username());
        assertEquals(List.of("ROLE_ADMIN", "ROLE_USER"), userInfo.roles());
    }

    @Test
    void resolveArgument_whenUnauthenticatedOrAnonymous_returnsAnonymousCurrentUserInfo() throws Exception {
        MethodParameter param = methodParam("withAnnotation", CurrentUserInfo.class);
        ServletWebRequest webRequest = new ServletWebRequest(new MockHttpServletRequest());

        SecurityContextHolder.clearContext();
        Object withoutContext = resolver.resolveArgument(param, null, webRequest, null);
        assertEquals(CurrentUserInfo.anonymous(), withoutContext);

        AnonymousAuthenticationToken anonymousAuth = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(anonymousAuth);
        Object withAnonymousAuth = resolver.resolveArgument(param, null, webRequest, null);
        assertEquals(CurrentUserInfo.anonymous(), withAnonymousAuth);
    }

    private MethodParameter methodParam(String methodName, Class<?> paramType) throws NoSuchMethodException {
        Method method = SampleControllerMethods.class.getDeclaredMethod(methodName, paramType);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private static final class SampleControllerMethods {
        void withAnnotation(@CurrentUser CurrentUserInfo user) {
        }

        void withoutAnnotation(CurrentUserInfo user) {
        }

        void plainParameter(String value) {
        }

        void wrongType(@CurrentUser String user) {
        }
    }
}
