package com.assessment.disputeportal.security.web;

import com.assessment.disputeportal.common.api.ApiEnvelope;
import com.assessment.disputeportal.common.web.ApiErrorWriter;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounds JSON bodies before message conversion while retaining the request for MVC. */
@RequiredArgsConstructor
public final class JsonRequestSizeLimitFilter extends OncePerRequestFilter {
  public static final int MAX_JSON_BODY_BYTES = 1_048_576;

  private final ApiErrorWriter errors;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      jakarta.servlet.http.HttpServletResponse response,
      jakarta.servlet.FilterChain chain)
      throws jakarta.servlet.ServletException, IOException {
    if (!isJson(request.getContentType())) {
      chain.doFilter(request, response);
      return;
    }
    if (request.getContentLengthLong() > MAX_JSON_BODY_BYTES) {
      tooLarge(response);
      return;
    }

    byte[] body = request.getInputStream().readNBytes(MAX_JSON_BODY_BYTES + 1);
    if (body.length > MAX_JSON_BODY_BYTES) {
      tooLarge(response);
      return;
    }
    chain.doFilter(new ReplayableJsonRequest(request, body), response);
  }

  private void tooLarge(jakarta.servlet.http.HttpServletResponse response) throws IOException {
    errors.write(
        response,
        HttpStatus.PAYLOAD_TOO_LARGE.value(),
        ApiEnvelope.failure(
            1006,
            "request-too-large",
            "Request too large",
            "The JSON request body exceeds the allowed size."));
  }

  private static boolean isJson(String contentType) {
    if (contentType == null) return false;
    String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    return mediaType.startsWith("application/")
        && (mediaType.endsWith("/json") || mediaType.endsWith("+json"));
  }

  private static final class ReplayableJsonRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    private ReplayableJsonRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
    }

    @Override
    public ServletInputStream getInputStream() {
      var input = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public int read() {
          return input.read();
        }

        @Override
        public boolean isFinished() {
          return input.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
          if (listener == null) throw new IllegalArgumentException("Read listener is required.");
          try {
            if (!isFinished()) listener.onDataAvailable();
            if (isFinished()) listener.onAllDataRead();
          } catch (IOException failure) {
            listener.onError(failure);
          }
        }
      };
    }

    @Override
    public BufferedReader getReader() throws IOException {
      String encoding = getCharacterEncoding();
      Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
      return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    @Override
    public int getContentLength() {
      return body.length;
    }

    @Override
    public long getContentLengthLong() {
      return body.length;
    }
  }
}
