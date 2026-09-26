package backend_project.backend_project.exception;

import backend_project.backend_project.model.Response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler  {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleException(NotFoundException notFoundException) {
        return build(notFoundException.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleException(BadRequestException badRequestException) {
        return build(badRequestException.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InternalServerErrorException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleException(InternalServerErrorException internalServerErrorException) {
        return build(internalServerErrorException.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(JwtAuthenticationException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleException(JwtAuthenticationException jwtAuthenticationException) {
        return build(jwtAuthenticationException.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleException(ForbiddenException forbiddenException) {
        return build(forbiddenException.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(NoContentException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleException(NoContentException noContentException) {
        return build(noContentException.getMessage(), HttpStatus.NO_CONTENT);
    }

    private ResponseEntity<ApiResponse<ErrorResponse>> build(String message, HttpStatus httpStatus) {

        ErrorResponse errorResponse = ErrorResponse.builder()
                .message(message)
                .statusCode(httpStatus.value())
                .build();

        ApiResponse<ErrorResponse> apiResponse = ApiResponse.<ErrorResponse>builder()
                .result(false)
                .data(errorResponse)
                .build();

        return new ResponseEntity<>(apiResponse, httpStatus);
    }
}
