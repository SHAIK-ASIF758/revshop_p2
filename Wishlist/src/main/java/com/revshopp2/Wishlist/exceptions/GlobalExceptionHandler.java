package com.revshopp2.Wishlist.exceptions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {

	// Handle generic exceptions
	@ExceptionHandler(Exception.class)
	public ModelAndView handleGeneralException(Exception ex) {
		ModelAndView mav = new ModelAndView();
		mav.addObject("message", "An unexpected error occurred.");
		mav.addObject("details", ex.getMessage());
		mav.setViewName("error"); // Default error.html view in templates folder
		return mav;
	}

}
