package com.starhub;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;

public final class SecurityFilter implements Filter {
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse res = (HttpServletResponse) response;
        request.setCharacterEncoding("UTF-8");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "DENY");
        res.setHeader("Referrer-Policy", "no-referrer");
        res.setHeader("Permissions-Policy", "camera=(), microphone=(self), geolocation=()");
        res.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: https: blob:; media-src 'self' data: https: blob:; connect-src 'self' wss://starhub.zhongjiehuang.com; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'");
        res.setHeader("Cache-Control", "no-store");
        chain.doFilter(request, response);
    }
}
