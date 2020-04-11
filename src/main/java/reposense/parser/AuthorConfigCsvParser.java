package reposense.parser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;

import reposense.model.Author;
import reposense.model.AuthorConfiguration;
import reposense.model.RepoLocation;

/**
 * Container for the values parsed from {@code author-config.csv} file.
 */
public class AuthorConfigCsvParser extends CsvParser<AuthorConfiguration> {
    public static final String AUTHOR_CONFIG_FILENAME = "author-config.csv";

    /**
     * Positions of the elements of a line in author-config.csv config file.
     */
    private static final int LOCATION_POSITION = 0;
    private static final int BRANCH_POSITION = 1;
    private static final int GITHUB_ID_POSITION = 2;
    private static final int EMAIL_POSITION = 3;
    private static final int DISPLAY_NAME_POSITION = 4;
    private static final int ALIAS_POSITION = 5;
    private static final int IGNORE_GLOB_LIST_POSITION = 6;
    private static final int HEADER_SIZE = IGNORE_GLOB_LIST_POSITION + 1; // last position + 1

    public AuthorConfigCsvParser(Path csvFilePath) throws IOException {
        super(csvFilePath, HEADER_SIZE);
    }

    /**
     * Gets the list of positions that are mandatory for verification.
     */
    @Override
    protected int[] mandatoryPositions() {
        return new int[] {
            GITHUB_ID_POSITION,
        };
    }

    /**
     * Processes the csv file line by line and add created {@code AuthorConfiguration} into {@code results} but
     * skips {@code author} already exists in a {@code AuthorConfiguration} that has same {@code location} and
     * {@code branch}.
     */
    @Override
    protected void processLine(List<AuthorConfiguration> results, CSVRecord record)
            throws ParseException {
        List<String> locations = getAsListOrDefault(record, LOCATION_POSITION, Collections.singletonList(""));
        String branch = getOrDefault(record, BRANCH_POSITION, AuthorConfiguration.DEFAULT_BRANCH);
        String gitHubId = get(record, GITHUB_ID_POSITION);
        List<String> emails = getAsList(record, EMAIL_POSITION);
        String displayName = get(record, DISPLAY_NAME_POSITION);
        List<String> aliases = getAsList(record, ALIAS_POSITION);
        List<String> ignoreGlobList = getAsList(record, IGNORE_GLOB_LIST_POSITION);

        for (String location : locations) {
            AuthorConfiguration config = findMatchingAuthorConfiguration(results, location, branch);

            Author author = new Author(gitHubId);

            if (config.containsAuthor(author)) {
                logger.warning(String.format(
                        "Skipping author as %s already in repository %s %s",
                        author.getGitId(), config.getLocation(), config.getBranch()));
                return;
            }

            config.addAuthor(author);
            setEmails(config, author, emails);
            setDisplayName(config, author, displayName);
            setAliases(config, author, gitHubId, aliases);
            setAuthorIgnoreGlobList(author, ignoreGlobList);
        }
    }


    /**
     * Gets an existing {@code AuthorConfiguration} from {@code results} if {@code location} and {@code branch} matches.
     * Otherwise adds a newly created {@code AuthorConfiguration} into {@code results} and returns it.
     *
     * @throws InvalidLocationException if {@code location} is invalid.
     */
    private static AuthorConfiguration findMatchingAuthorConfiguration(
            List<AuthorConfiguration> results, String location, String branch) throws InvalidLocationException {
        RepoLocation repoLocation = new RepoLocation(location);
        AuthorConfiguration config = new AuthorConfiguration(repoLocation,
                repoLocation.getParsedBranch().orElse(branch));

        for (AuthorConfiguration authorConfig : results) {
            if (authorConfig.getLocation().equals(config.getLocation())
                    && authorConfig.getBranch().equals(config.getBranch())) {
                return authorConfig;
            }
        }

        results.add(config);
        return config;
    }

    /**
     * Associates {@code emails} to {@code author}, if provided and not empty.
     */
    private static void setEmails(AuthorConfiguration config, Author author, List<String> emails) {
        author.setEmails(new ArrayList<>(emails));
        config.addAuthorEmailsAndAliasesMapEntry(author, author.getEmails());
    }

    /**
     * Associates {@code displayName} to {@code author}, if provided and not empty.
     * Otherwise, use github id from {@code author}.
     */
    private static void setDisplayName(AuthorConfiguration config, Author author, String displayName) {
        author.setDisplayName(!displayName.isEmpty() ? displayName : author.getGitId());
        config.setAuthorDisplayName(author, !displayName.isEmpty() ? displayName : author.getGitId());
    }

    /**
     * Associates {@code gitHubId} and additional {@code aliases} to {@code author}.
     */
    private static void setAliases(AuthorConfiguration config, Author author, String gitHubId, List<String> aliases) {
        config.addAuthorEmailsAndAliasesMapEntry(author, Arrays.asList(gitHubId));

        if (aliases.isEmpty()) {
            return;
        }

        config.addAuthorEmailsAndAliasesMapEntry(author, aliases);
        author.setAuthorAliases(aliases);
    }

    /**
     * Sets the list of globs to ignore for the {@code author} for file analysis.
     */
    private static void setAuthorIgnoreGlobList(Author author, List<String> ignoreGlobList) {
        if (ignoreGlobList.isEmpty()) {
            return;
        }

        author.setIgnoreGlobList(ignoreGlobList);
    }
}
