package nl.inl.blacklab.server.lib.results;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.server.exceptions.BlsIndexOpenException;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.lib.SearchCreator;

public class ResultServerInfo {

    static final Logger logger = LogManager.getLogger(ResultServerInfo.class);

    private final boolean debugMode;

    private final SearchCreator params;

    private final ResultUserInfo userInfo;

    private final List<ResultIndexStatus> indexStatuses;

    ResultServerInfo(SearchCreator params, boolean debugMode) {
        this.params = params;
        this.debugMode = debugMode;

        userInfo = WebserviceOperations.userInfo(params);
        indexStatuses = new ArrayList<>();
        Collection<Index> indices = params.getIndexManager().getAllAvailableIndices(params.getUser().getUserId());
        for (Index index: indices) {
            try {
                indexStatuses.add(WebserviceOperations.resultIndexStatus(index));
            } catch (BlsIndexOpenException e) {
                // Cannot open this index; log and skip it.
                logger.warn("Could not open index " + index.getId() + ": " + e.getMessage());
            }
        }
    }

    public SearchCreator getParams() {
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
