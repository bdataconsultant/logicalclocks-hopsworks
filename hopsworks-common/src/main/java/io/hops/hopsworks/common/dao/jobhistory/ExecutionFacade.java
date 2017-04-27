package io.hops.hopsworks.common.dao.jobhistory;

import io.hops.hopsworks.common.jobs.jobhistory.JobFinalStatus;
import io.hops.hopsworks.common.jobs.jobhistory.JobState;
import io.hops.hopsworks.common.jobs.jobhistory.JobType;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import io.hops.hopsworks.common.dao.AbstractFacade;
import io.hops.hopsworks.common.dao.jobs.JobInputFile;
import io.hops.hopsworks.common.dao.jobs.JobOutputFile;
import io.hops.hopsworks.common.dao.jobs.description.JobDescription;
import io.hops.hopsworks.common.dao.project.Project;
import io.hops.hopsworks.common.dao.user.Users;

/**
 * Facade for management of persistent Execution objects.
 */
@Stateless
public class ExecutionFacade extends AbstractFacade<Execution> {

  private static final Logger logger = Logger.getLogger(ExecutionFacade.class.
          getName());

  @PersistenceContext(unitName = "kthfsPU")
  private EntityManager em;

  public ExecutionFacade() {
    super(Execution.class);
  }

  @Override
  protected EntityManager getEntityManager() {
    return em;
  }

  private HashMap<Integer, Execution> executions = new HashMap<>();

  /**
   * Find all the Execution entries for the given project and type.
   * <p/>
   * @param project
   * @param type
   * @return List of JobHistory objects.
   * @throws IllegalArgumentException If the given JobType is not supported.
   */
  public List<Execution> findForProjectByType(Project project, JobType type)
          throws IllegalArgumentException {
    TypedQuery<Execution> q = em.createNamedQuery(
            "Execution.findByProjectAndType", Execution.class);
    q.setParameter("type", type);
    q.setParameter("project", project);
    return q.getResultList();
  }

  /**
   * Get all the executions for a given JobDescription.
   * <p/>
   * @param job
   * @return
   */
  public List<Execution> findForJob(JobDescription job) {
    TypedQuery<Execution> q = em.createNamedQuery("Execution.findByJob",
            Execution.class);
    q.setParameter("job", job);
    return q.getResultList();
  }

  /**
   * Get an execution for application id.
   * <p/>
   * @param appId
   * @return
   */
  public Execution findByAppId(String appId) {
    try {
      return em.createNamedQuery("Execution.findByAppId",
              Execution.class).setParameter("appId", appId).getSingleResult();
    } catch (NoResultException e) {
      return null;
    }
  }

  public List<Execution> findbyProjectAndJobId(Project project, int jobId) {
    TypedQuery<Execution> q = em.createNamedQuery(
            "Execution.findByProjectAndJobId",
            Execution.class);
    q.setParameter("jobid", jobId);
    q.setParameter("project", project);
    return q.getResultList();
  }

  /**
   * Find the execution with given id.
   * <p/>
   * @param id
   * @return The found entity, or null if no such exists.
   */
  public Execution findById(Integer id) {
    return em.find(Execution.class, id);
  }

  public Execution create(JobDescription job, Users user, String stdoutPath,
          String stderrPath, Collection<JobInputFile> input,
          JobFinalStatus finalStatus, float progress) {
    return create(job, user, JobState.INITIALIZING, stdoutPath, stderrPath,
            input, finalStatus, progress);
  }

  public Execution create(JobDescription job, Users user, JobState state,
          String stdoutPath,
          String stderrPath, Collection<JobInputFile> input,
          JobFinalStatus finalStatus, float progress) {
    //Check if state is ok
    if (state == null) {
      state = JobState.INITIALIZING;
    }
    if (finalStatus == null) {
      finalStatus = JobFinalStatus.UNDEFINED;
    }
    //Create new object
    Execution exec = new Execution(state, job, user, stdoutPath, stderrPath,
            input, finalStatus, progress);
    //And persist it
    em.persist(exec);
    em.flush();
    return exec;
  }

  public Execution updateState(Execution exec, JobState newState) {
    return update(exec, newState, -1, null, null, null, null,
            null, null, 0);
  }

  public Execution updateFinalStatus(Execution exec, JobFinalStatus finalStatus) {
    return update(exec, null, -1, null, null, null, null,
            null, finalStatus, 0);
  }

  public Execution updateProgress(Execution exec, float progress) {
    return update(exec, null, -1, null, null, null, null,
            null, null, progress);
  }

  public Execution updateExecutionTime(Execution exec, long executionTime) {
    return update(exec, null, executionTime, null, null, null, null, null, null,
            0);
  }

  public Execution updateOutput(Execution exec,
          Collection<JobOutputFile> outputFiles) {
    return update(exec, null, -1, null, null, null, outputFiles, null, null, 0);
  }

  public Execution updateStdOutPath(Execution exec, String stdOutPath) {
    return update(exec, null, -1, stdOutPath, null, null, null, null, null, 0);
  }

  public Execution updateStdErrPath(Execution exec, String stdErrPath) {
    return update(exec, null, -1, null, stdErrPath, null, null, null, null, 0);
  }

  public Execution updateAppId(Execution exec, String appId) {
    return update(exec, null, -1, null, null, appId, null, null, null, 0);
  }

  /**
   * Updates all given fields of <i>exec</i> to the given value, unless that
   * value is null for entities, or -1 for integers.
   * <p/>
   * @param exec
   * @param state
   * @param executionDuration
   * @param stdoutPath
   * @param stderrPath
   * @param appId
   * @param output
   * @param input
   * @param finalStatus
   */
  private Execution update(
          Execution exec, JobState state, long executionDuration,
          String stdoutPath, String stderrPath, String appId,
          Collection<JobOutputFile> output, Collection<JobInputFile> input,
          JobFinalStatus finalStatus, float progress) {
    //Find the updated execution object
    Execution obj = em.find(Execution.class, exec.getId());
    int count = 0;
    while (obj == null && count < 10) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException ex) {
        logger.log(Level.SEVERE, null, ex);
      }
      logger.info("Trying to get the Execution Object");
      obj = em.find(Execution.class, exec.getId());
      count++;
    }
    if (obj == null) {
      throw new IllegalArgumentException(
              "Unable to find Execution object with id " + exec.getId());
    } else {
      exec = obj;
    }
    if (state != null) {
      exec.setState(state);
    }
    if (executionDuration != -1) {
      exec.setExecutionDuration(executionDuration);
    }
    if (stdoutPath != null) {
      exec.setStdoutPath(stdoutPath);
    }
    if (stderrPath != null) {
      exec.setStderrPath(stderrPath);
    }
    if (appId != null) {
      exec.setAppId(appId);
    }
    if (input != null) {
      exec.setJobInputFileCollection(input);
    }
    if (output != null) {
      exec.setJobOutputFileCollection(output);
    }
    if (finalStatus != null) {
      exec.setFinalStatus(finalStatus);
    }
    if (progress != 0) {
      exec.setProgress(progress);
    }
    em.merge(exec);
    executions.put(exec.getJob().getId(), exec);
    return exec;
  }

  public Execution getExecution(int id) {
    try {
      return executions.get(id);
    } catch (Exception e) {
      return null;
    }
  }

}