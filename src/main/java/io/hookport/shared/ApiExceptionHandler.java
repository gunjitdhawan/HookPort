package io.hookport.shared;

import io.hookport.security.UnsafeTargetUrlException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockingFailure(
            OptimisticLockingFailureException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.PRECONDITION_FAILED,
                "The resource was modified by another request"
        );

        problem.setTitle("Concurrent modification");

        return problem;
    }

    @ExceptionHandler(UnsafeTargetUrlException.class)
    public ResponseEntity<ProblemDetail> handleUnsafeTarget(
            UnsafeTargetUrlException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(
                HttpStatus.BAD_REQUEST
        );

        problem.setTitle("Unsafe webhook target");
        problem.setDetail(exception.getMessage());

        return ResponseEntity.badRequest().body(problem);
    }
}