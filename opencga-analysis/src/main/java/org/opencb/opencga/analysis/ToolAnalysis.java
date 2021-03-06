package org.opencb.opencga.analysis;

import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.hpg.bigdata.analysis.exceptions.AnalysisToolException;
import org.opencb.hpg.bigdata.analysis.tools.ToolManager;
import org.opencb.hpg.bigdata.analysis.tools.manifest.Param;
import org.opencb.opencga.catalog.db.api.FileDBAdaptor;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.managers.CatalogManager;
import org.opencb.opencga.catalog.managers.FileManager;
import org.opencb.opencga.catalog.managers.JobManager;
import org.opencb.opencga.core.config.Configuration;
import org.opencb.opencga.core.models.File;
import org.opencb.opencga.core.models.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolAnalysis {

    private Logger logger = LoggerFactory.getLogger(ToolAnalysis.class);

    private CatalogManager catalogManager;
    private ToolManager toolManager;

    private JobManager jobManager;
    private FileManager fileManager;

    public ToolAnalysis(Configuration configuration) throws CatalogException, AnalysisToolException {
        this.catalogManager = new CatalogManager(configuration);
        this.toolManager = new ToolManager(Paths.get(configuration.getToolDir()));

        this.jobManager = catalogManager.getJobManager();
        this.fileManager = catalogManager.getFileManager();
    }

    /**
     * Execute a command tool.
     * @param jobId jobId of the job containing the relevant information.
     * @param sessionId session id of the user that will execute the tool.
     */
    public void execute(long jobId, String sessionId) {
        try {
            // We get the job information.
            Job job = jobManager.get(jobId, QueryOptions.empty(), sessionId).first();
            long studyId = jobManager.getStudyId(jobId);

            String outDir = (String) job.getAttributes().get(Job.OPENCGA_TMP_DIR);
            Path outDirPath = Paths.get(outDir);

            String tool = job.getToolId();
            String execution = job.getExecution();

            // Create the OpenCGA output folder
            fileManager.createFolder(String.valueOf(studyId), (String) job.getAttributes().get(Job.OPENCGA_OUTPUT_DIR),
                    new File.FileStatus(), true, "", QueryOptions.empty(), sessionId);

            // Convert the input and output files to uris in the filesystem
            Map<String, String> params = new HashMap<>(job.getParams());
            List<Param> inputParams = toolManager.getInputParams(tool, execution);
            QueryOptions options = new QueryOptions(QueryOptions.INCLUDE, FileDBAdaptor.QueryParams.URI.key());
            for (Param inputParam : inputParams) {
                if (inputParam.isRequired() && !params.containsKey(inputParam.getName())) {
                    throw new CatalogException("Missing mandatory input parameter " + inputParam.getName());
                }
                if (params.containsKey(inputParam.getName())) {
                    // Get the file uri
                    String fileString = params.get(inputParam.getName());
                    QueryResult<File> fileQueryResult = fileManager.get(String.valueOf(studyId), fileString, options, sessionId);
                    if (fileQueryResult.getNumResults() == 0) {
                        throw new CatalogException("File " + fileString + " not found");
                    }
                    params.put(inputParam.getName(), fileQueryResult.first().getUri().getPath());
                }
            }

            // Convert output file params to be stored in the output directory specified
            List<Param> outputParams = toolManager.getOutputParams(tool, execution);
            for (Param outputParam : outputParams) {
                if (outputParam.isRequired() && !params.containsKey(outputParam.getName())) {
                    throw new CatalogException("Missing mandatory output parameter " + outputParam.getName());
                }
                if (params.containsKey(outputParam.getName())) {
                    // Contextualise the file name in the uri where it should be written. /jobs/jobX/file.txt where /jobs/jobX = outDir and
                    // file.txt = outputFileName
                    Path name = Paths.get(params.get(outputParam.getName()));
                    String absolutePath = outDirPath.resolve(name.toFile().getName()).toString();
                    params.put(outputParam.getName(), absolutePath);
                }
            }

            // Execute the tool
            String commandLine = toolManager.createCommandLine(tool, execution, params);
            toolManager.runCommandLine(commandLine, Paths.get(outDir), false);
        } catch (CatalogException | AnalysisToolException e) {
            logger.error("{}", e.getMessage(), e);
        }
    }

}
