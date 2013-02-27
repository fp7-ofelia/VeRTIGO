/**
 *
 */
package org.flowvisor.exceptions;

/**
 * Signal that an action in an actions list was not allowed
 *
 * @author capveg
 *
 */
public class ActionDisallowedException extends FVException {

	public ActionDisallowedException(String string) {
		super(string);
	}

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

}
