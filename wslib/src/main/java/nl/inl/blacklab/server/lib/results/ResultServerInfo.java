package nl.inl.blacklab.server.lib.results;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.lib.WebserviceParams;

public class ResultServerInfo {

    static final Logger logger = LogManager.getLogger(ResultServerInfo.class);

    private final boolean debugMode;

    private final WebserviceParams params;

    private final ResultUserInfo userInfo;

    private final List<ResultIndexStatus> indexStatuses;

    ResultServerInfo(WebserviceParams params, boolean debugMode) {
        this.params = params;
        this.debugMode = debugMode;

        userInfo = WebserviceOperations.userInfo(params);
        indexStatuses = new ArrayList<>();
        Collection<Index> indices = params.getIndexManager().getAllAvailableCorpora(params.getUser());
        for (Index index: indices) {
            try {
                indexStatuses.add(WebserviceOperations.resultIndexStatus(index, params.getUser()));
            } catch (ErrorOpeningIndex e) {
                // Cannot open this index; log and skip it.
                logger.warn("Could not open index " + index.getId() + ": " + e.getMessage());
            }
        }
    }

    public WebserviceParams getParams() {
        return params;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public ResultUserInfo getUserInfo() {
        return userInfo;
    }

    public List<ResultIndexStatus> getIndexStatuses() {
        return indexStatuses;
    }
}
