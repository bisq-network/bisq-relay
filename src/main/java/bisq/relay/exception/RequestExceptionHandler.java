/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.relay.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Handles request-level exceptions for the REST API.
 * <p>
 * Exceptions such as validation failures, malformed request bodies, unsupported
 * methods, unsupported media types, and missing routes can occur before
 * controller methods return their own {@link ResponseEntity}. Handling them here
 * keeps error handling explicit and ensures consistent HTTP status responses for
 * request failures.
 * <p>
 * Error responses intentionally use an empty body. Requests may contain
 * sensitive values such as device tokens or encrypted notification payloads, and
 * framework exception messages can include request details. Callers should rely on
 * the HTTP status code rather than response body details.
 */
@RestControllerAdvice
public class RequestExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RequestExceptionHandler.class);

    /**
     * Handles requests that do not match any controller endpoint.
     *
     * @param ex the no-route exception raised by Spring MVC
     * @return an empty response with HTTP 404
     */
    @ExceptionHandler({
            NoHandlerFoundException.class,
            NoResourceFoundException.class
    })
    public ResponseEntity<Void> handleNotFound(Exception ex) {
        LOG.warn("Rejected request with no matching endpoint: {}", ex.getClass().getSimpleName());
        return emptyErrorResponse(HttpStatus.NOT_FOUND);
    }

    /**
     * Handles malformed or invalid client requests.
     * <p>
     * This includes application-level bad arguments, bean validation failures,
     * missing parameters, malformed JSON bodies, multipart parsing errors, and
     * parameter type mismatches.
     *
     * @param ex the bad-request exception raised while resolving or validating the request
     * @return an empty response with HTTP 400
     */
    @ExceptionHandler({
            BadArgumentsException.class,
            MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MultipartException.class
    })
    public ResponseEntity<Void> handleBadRequest(Exception ex) {
        LOG.warn("Rejected bad request: {}", ex.getClass().getSimpleName());
        return emptyErrorResponse(HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles requests using an unsupported HTTP method.
     *
     * @param ex the unsupported-method exception raised by Spring MVC
     * @return an empty response with HTTP 405
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Void> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        LOG.warn("Rejected request with unsupported method: {}", ex.getClass().getSimpleName());
        return emptyErrorResponse(HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * Handles requests using an unsupported content type.
     *
     * @param ex the unsupported-media-type exception raised by Spring MVC
     * @return an empty response with HTTP 415
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Void> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        LOG.warn("Rejected request with unsupported media type: {}", ex.getClass().getSimpleName());
        return emptyErrorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    /**
     * Builds an empty error response.
     * <p>
     * This intentionally does not set {@code Content-Type}, because the response
     * body is empty.
     *
     * @param status the HTTP status to return
     * @return an empty response with the given HTTP status
     */
    private ResponseEntity<Void> emptyErrorResponse(HttpStatus status) {
        return ResponseEntity
                .status(status)
                .build();
    }
}
