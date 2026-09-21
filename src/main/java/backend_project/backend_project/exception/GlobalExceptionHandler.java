package backend_project.backend_project.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler  {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleException(NotFoundException notFoundException) {
        ErrorResponse errorResponse = new ErrorResponse(
                notFoundException.getMessage(),
                HttpStatus.NOT_FOUND.value());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleException(BadRequestException badRequestException) {
        ErrorResponse errorResponse = new ErrorResponse(
                badRequestException.getMessage(),
                HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InternalServerErrorException.class)
    public ResponseEntity<ErrorResponse> handleException(InternalServerErrorException internalServerErrorException) {
        ErrorResponse errorResponse = new ErrorResponse(
                internalServerErrorException.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR.value());
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(JwtAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleException(JwtAuthenticationException jwtAuthenticationException) {
        ErrorResponse errorResponse = new ErrorResponse(
                jwtAuthenticationException.getMessage(),
                HttpStatus.UNAUTHORIZED.value());
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleException(ForbiddenException forbiddenException) {
        ErrorResponse errorResponse = new ErrorResponse(
                forbiddenException.getMessage(),
                HttpStatus.FORBIDDEN.value());
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(NoContentException.class)
    public ResponseEntity<ErrorResponse> handleException(NoContentException noContentException) {
        ErrorResponse errorResponse = new ErrorResponse(
                noContentException.getMessage(),
                HttpStatus.NO_CONTENT.value());
        return new ResponseEntity<>(errorResponse, HttpStatus.NO_CONTENT);
    }
}
