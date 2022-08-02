package com.mastercard.test.flow.example.app;

import com.mastercard.test.flow.example.framework.Operation;
import com.mastercard.test.flow.example.framework.Operation.Query;
import com.mastercard.test.flow.example.framework.Service;

/**
 * The gateway service that allows system interaction via web browser
 */
public interface WebUi extends Service {

	/**
	 * @return The HTML home page, which supplies the forms that provoke behaviour
	 */
	@Operation(method = "GET", path = "/web",
			reqContentType = "application/text",
			resContentType = "text/html")
	String home();

	/**
	 * @param subject    The text to count the characters of
	 * @param characters The characters to count, or the empty string to count all
	 *                   characters
	 * @return A HTML page that shows the operation results
	 */
	@Operation(method = "POST", path = "/web/process",
			reqContentType = "application/x-www-form-urlencoded",
			resContentType = "text/html")
	String process(
			@Query("subject") String subject, @Query("characters") String characters );
}
