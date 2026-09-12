package com.hmdp.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestTraceFilterTest {

    private final RequestTraceFilter filter = new RequestTraceFilter();

    @Test
    void keepsSafeCallerTraceIdAndClearsMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTraceFilter.TRACE_HEADER, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("request-123", response.getHeader(RequestTraceFilter.TRACE_HEADER));
        assertNull(MDC.get("traceId"));
    }

    @Test
    void replacesUnsafeTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestTraceFilter.TRACE_HEADER, "bad trace/value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String traceId = response.getHeader(RequestTraceFilter.TRACE_HEADER);
        assertFalse(traceId.contains(" "));
        assertFalse(traceId.contains("/"));
        assertEquals(32, traceId.length());
    }
}
