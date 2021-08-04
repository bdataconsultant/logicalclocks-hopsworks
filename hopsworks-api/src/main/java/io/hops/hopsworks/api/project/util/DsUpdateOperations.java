/*
 * This file is part of Hopsworks
 * Copyright (C) 2018, Logical Clocks AB. All rights reserved
 *
 * Hopsworks is free software: you can redistribute it and/or modify it under the terms of
 * the GNU Affero General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * Hopsworks is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.  See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package io.hops.hopsworks.api.project.util;

import io.hops.hopsworks.api.filter.AllowedProjectRoles;
import io.hops.hopsworks.common.dao.project.team.ProjectTeamFacade;
import io.hops.hopsworks.common.dataset.DatasetController;
import io.hops.hopsworks.common.hdfs.DistributedFileSystemOps;
import io.hops.hopsworks.common.hdfs.DistributedFsService;
import io.hops.hopsworks.common.hdfs.HdfsUsersController;
import io.hops.hopsworks.exceptions.DatasetException;
import io.hops.hopsworks.exceptions.HopsSecurityException;
import io.hops.hopsworks.exceptions.ProjectException;
import io.hops.hopsworks.persistence.entity.dataset.Dataset;
import io.hops.hopsworks.persistence.entity.project.Project;
import io.hops.hopsworks.persistence.entity.user.Users;
import io.hops.hopsworks.restutils.RESTCodes;
import org.apache.hadoop.security.AccessControlException;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;

/**
 * Helper bean for dataset deletion and creation methods that are shared across several services
 */
@Stateless
public class DsUpdateOperations {

  @EJB
  private PathValidator pathValidator;
  @EJB
  private DistributedFsService dfs;
  @EJB
  private HdfsUsersController hdfsUsersBean;
  @EJB
  private DatasetController datasetController;
  @EJB
  private ProjectTeamFacade projectTeamFacade;

  /**
   * Deletes a file inside a top-level dataset
   *
   * @param project  the project of the user making the request
   * @param user     the user making the request
   * @param fileName the name of the folder or file to remove
   * @return the fullpath of the deleted file
   * @throws DatasetException
   * @throws ProjectException
   */
  public org.apache.hadoop.fs.Path deleteDatasetFile(
      Project project, Users user, String fileName) throws DatasetException, ProjectException, HopsSecurityException,
      UnsupportedEncodingException {
    boolean success = false;
    DistributedFileSystemOps dfso = null;
    DsPath dsPath = pathValidator.validatePath(project, fileName);
    Dataset ds = dsPath.getDs();

    org.apache.hadoop.fs.Path fullPath = dsPath.getFullPath();
    org.apache.hadoop.fs.Path dsRelativePath = dsPath.getDsRelativePath();

    if (dsRelativePath.depth() == 0) {
      throw new IllegalArgumentException("Use endpoint DELETE /{datasetName} to delete top level dataset)");
    }

    try {
      String username = hdfsUsersBean.getHdfsUserName(project, user);
      //If a Data Scientist requested it, do it as project user to avoid deleting Data Owner files
      //Find project of dataset as it might be shared
      Project owning = datasetController.getOwningProject(ds);
      boolean isMember = projectTeamFacade.isUserMemberOfProject(owning, user);
      if (isMember && projectTeamFacade.findCurrentRole(owning, user)
          .equals(AllowedProjectRoles.DATA_OWNER) && owning.equals(project)) {
        dfso = dfs.getDfsOps();// do it as super user
      } else {
        dfso = dfs.getDfsOps(username);// do it as project user
      }
      success = dfso.rm(fullPath, true);
    } catch(AccessControlException ex) {
      throw new HopsSecurityException(RESTCodes.SecurityErrorCode.HDFS_ACCESS_CONTROL, Level.FINE,
          "Operation: delete, path: " + fullPath.toString(), ex.getMessage(), ex);
    }
    catch (IOException ex) {
      throw new DatasetException(RESTCodes.DatasetErrorCode.INODE_DELETION_ERROR, Level.SEVERE,
          "path: " + fullPath.toString(), ex.getMessage(), ex);
    } finally {
      if (dfso != null) {
        dfs.closeDfsClient(dfso);
      }
    }
    if (!success) {
      throw new DatasetException(RESTCodes.DatasetErrorCode.INODE_DELETION_ERROR, Level.FINE,
          "path: " + fullPath.toString());
    }
    return fullPath;
  }
}
