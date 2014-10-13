/**
 * Sapelli data collection platform: http://sapelli.org
 * 
 * Copyright 2012-2014 University College London - ExCiteS group
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package uk.ac.ucl.excites.sapelli.collector.db;

import java.util.List;

import uk.ac.ucl.excites.sapelli.collector.model.Project;
import uk.ac.ucl.excites.sapelli.collector.model.fields.Relationship;
import uk.ac.ucl.excites.sapelli.collector.util.DuplicateException;
import uk.ac.ucl.excites.sapelli.shared.db.Store;
import uk.ac.ucl.excites.sapelli.storage.model.RecordReference;

/**
 * Abstract super class for Project storage back-ends
 * 
 * @author mstevens
 */
public abstract class ProjectStore implements Store
{

	/**
	 * @param project
	 * @throws DuplicateException
	 */
	static public void ThrowDuplicateProjectSignatureException(Project project) throws DuplicateException
	{
		throw new DuplicateException("There is already a project with signature \"" + project.toString(false) + "\". Either delete the existing one or change the version of the new one.");
	}
	
	/**
	 * For backwards compatibility only
	 * 
	 * @param id
	 * @param version
	 * @return
	 */
	public abstract Project retrieveV1Project(int schemaID, int schemaVersion);

	/**
	 * @param project
	 */
	public abstract void store(Project project) throws DuplicateException;

	/**
	 * Retrieves all projects
	 * 
	 * @return
	 */
	public abstract List<Project> retrieveProjects();
	
	/**
	 * Retrieves specific Project
	 * 
	 * @return null if project was not found
	 */
	public Project retrieveProject(final String name, final String version)
	{
		return retrieveProject(name, null, version);
	}

	/**
	 * Retrieves specific Project
	 * 
	 * @return null if project was not found
	 */
	public abstract Project retrieveProject(String name, String variant, String version);

	/**
	 * Retrieves specific Project, identified by id and fingerprint
	 * 
	 * @param projectID
	 * @param projectFingerPrint
	 * @return null if no such project was found
	 */
	public abstract Project retrieveProject(int projectID, int projectFingerPrint);
	
	/**
	 * Retrieves all project versions/variants which share a given ID
	 * 
	 * @param projectID
	 * @return list of projects
	 */
	public abstract List<Project> retrieveProjectVersions(int projectID);

	/**
	 * Delete specific project
	 * 
	 * @return
	 */
	public abstract void delete(Project project);
	
	public abstract void storeHeldForeignKey(Relationship relationship, RecordReference foreignKey);
	
	public abstract RecordReference retrieveHeldForeignKey(Relationship relationship);
	
	public abstract void deleteHeldForeignKey(Relationship relationship);

}