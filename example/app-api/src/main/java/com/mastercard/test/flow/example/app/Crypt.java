package com.mastercard.test.flow.example.app;

import com.mastercard.test.flow.example.framework.Operation;
import com.mastercard.test.flow.example.framework.Operation.Body;
import com.mastercard.test.flow.example.framework.Service;

/**
 * Encryption operations
 */
public interface Crypt extends Service {

	/**
	 * @param plaintext data to be encrypted
	 * @return The cipher text
	 */
	@Operation(method = "GET", path = "/encrypt")
	String encrypt( @Body String plaintext );

	/**
	 * @param cipherText As produced by {@link #encrypt(String)}
	 * @return The plain text
	 */
	@Operation(method = "GET", path = "/decrypt")
	String decrypt( @Body String cipherText );
}
