package uk.ac.shef

import java.lang.Exception

/**
 * Exception type thrown by all native methods upon failure
 *
 * @param[message] The associated error message
 *
 * @author Elliot Steele
 */
class U5Exception(message : String) : Exception(message)
