/**
 * 
 */
package uk.ac.ucl.excites.sapelli.storage.db.sql.sqlite.statements;

import uk.ac.ucl.excites.sapelli.shared.db.DBException;
import uk.ac.ucl.excites.sapelli.storage.db.sql.sqlite.ISQLiteStatement;

/**
 * @author mstevens
 *
 */
public interface ISQLiteCUDStatement extends ISQLiteStatement
{

	public void executeCUD() throws DBException;
	
}
