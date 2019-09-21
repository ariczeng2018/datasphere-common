/*
 * Copyright 2019, Huahuidata, Inc.
 * DataSphere is licensed under the Mulan PSL v1.
 * You can use this software according to the terms and conditions of the Mulan PSL v1.
 * You may obtain a copy of Mulan PSL v1 at:
 * http://license.coscl.org.cn/MulanPSL
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR
 * PURPOSE.
 * See the Mulan PSL v1 for more details.
 */

package com.datasphere.server.common.exception;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.common.exceptions.InvalidGrantException;
import org.springframework.security.oauth2.common.exceptions.InvalidTokenException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.datasphere.server.common.CommonLocalVariable;
import com.datasphere.server.common.exception.DSSException;
import com.datasphere.server.domain.dataprep.exceptions.PrepException;
import com.datasphere.server.domain.engine.DruidEngineMetaRepository;

@RestControllerAdvice(basePackages = {"com.datasphere.server", "org.springframework.security"})
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @Autowired
  DruidEngineMetaRepository engineMetaRepository;

  @Value("${polaris.common.exception.printTrace:true}")
  private boolean printStackTrace;

  /**
   * 从连接客户端终止时的进程（正在进行连接的进程）
   */
  @ExceptionHandler(IOException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  public ResponseEntity<Object> exceptionHandler(IOException ex, WebRequest request) {

    if (StringUtils.containsIgnoreCase(ExceptionUtils.getRootCauseMessage(ex), "Broken pipe")) {
      String queryId = CommonLocalVariable.getLocalVariable().getQueryId();
      if (StringUtils.isNotEmpty(queryId)) {
        engineMetaRepository.cancelQuery(queryId);
      }
      return null;  // Socket is closed, cannot return any response
    }

    return handleAll(ex, request);
  }

  @ExceptionHandler(value = {DSSException.class})
  protected ResponseEntity<Object> handleDSSException(DSSException ex, WebRequest request) {

    HttpStatus status;
    ErrorResponse response;

    ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);

    String details = ExceptionUtils.getRootCauseMessage(ex);

    if (responseStatus != null) {
      status = responseStatus.code();
      response = new ErrorResponse(ex.getCode(), ex.getMessage(), details);
    } else {
      status = HttpStatus.INTERNAL_SERVER_ERROR;
      response = new ErrorResponse(ex.getCode(), DSSException.DEFAULT_GLOBAL_MESSAGE, details);
    }

    LOGGER.error("[API:{}] {} {}: {}", ((ServletWebRequest) request).getRequest().getRequestURI(),
                 response.getCode() == null ? "" : response.getCode(),
                 response.getMessage(),
                 response.getDetails());

    if(printStackTrace) {
      ex.printStackTrace();
    }

    return handleExceptionInternal(ex, response, new HttpHeaders(), status, request);
  }

  @ExceptionHandler(value = {PrepException.class})
  protected ResponseEntity<Object> handlePrepException(PrepException ex, WebRequest request) {

    HttpStatus status;
    ErrorResponse response;

    ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);

    if (responseStatus != null) {
      status = responseStatus.code();
      response = new ErrorResponse(ex.getCode(), ex.getMessageKey(), ex.getMessageDetail());
    } else {
      status = HttpStatus.INTERNAL_SERVER_ERROR;
      response = new ErrorResponse(ex.getCode(), DSSException.DEFAULT_GLOBAL_MESSAGE, ex.getMessage());
    }

    LOGGER.error("[API:{}] {} {}: {}", ((ServletWebRequest) request).getRequest().getRequestURI(),
            response.getCode() == null ? "" : response.getCode(),
            response.getMessage(),
            response.getDetails());

    return handleExceptionInternal(ex, response, new HttpHeaders(), status, request);
  }

  @ExceptionHandler(value = {AccessDeniedException.class})
  protected ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
    return handleDSSException(new com.datasphere.server.common.exception.AccessDeniedException(ex), request);
  }

  @ExceptionHandler(value = {InvalidTokenException.class})
  protected ResponseEntity<Object> handleInvalidTokenException(InvalidTokenException ex, WebRequest request) {
    return handleDSSException(new com.datasphere.server.common.exception.InvalidTokenException(ex), request);
  }

  @ExceptionHandler(value = {InvalidGrantException.class})
  protected ResponseEntity<Object> handleAuthenticationException(InvalidGrantException ex, WebRequest request) {
    return handleDSSException(new AuthenticationException(ex), request);
  }

  @ExceptionHandler({MethodArgumentTypeMismatchException.class})
  public ResponseEntity<Object> handleMethodTypeMismatchException(MethodArgumentTypeMismatchException ex, WebRequest request) {
    return handleDSSException(new BadRequestException(ex), request);
  }

  @ExceptionHandler({Exception.class, RuntimeException.class})
  public ResponseEntity<Object> handleAll(Exception ex, WebRequest request) {
    return handleDSSException(new UnknownServerException(ex), request);
  }

}
