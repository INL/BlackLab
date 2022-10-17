package nl.inl.blacklab.server.lib.results;

import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.index.DocIndexerFactory.Format;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.server.index.DocIndexerFactoryUserFormats;
import nl.inl.blacklab.server.index.DocIndexerFactoryUserFormats.IllegalUserFormatIdentifier;
import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.lib.User;

public class ResultListInputFormats {

    private final ResultUserInfo userInfo;

    private final List<Format> formats;

    ResultListInputFormats(WebserviceParams params) {
        userInfo = WebserviceOperations.userInfo(params);

        // List all available input formats
        User user = params.getUser();
        IndexManager indexMan = params.getIndexManager();
        ;
        if (user.isLoggedIn() && indexMan.getUserFormatManager() != null) {
            // Make sure users's formats are loaded
            indexMan.getUserFormatManager().loadUserFormats(user.getUserId());
        }
        formats = new ArrayList<>();
        for (Format format: DocumentFormats.getFormats()) {
            try {
                String userId = DocIndexerFactoryUserFormats.getUserIdOrFormatName(format.getId(), false);
                // Other user's formats are not explicitly enumerated (but should still be considered public)
                if (!userId.equals(userInfo.getUserId()))
                    continue;
            } catch (IllegalUserFormatIdentifier e) {
                // Alright, it's evidently not a user format, that means it's public. List it.
            }
            formats.add(format);
        }
    }

    public ResultUserInfo getUserInfo() {
        return userInfo;
    }

    public List<Format> getFormats() {
        return formats;
    }
}
