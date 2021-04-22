package com.xyhsoft.plugin;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.Writer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.MavenReportException;
import org.apache.maven.scm.ChangeFile;
import org.apache.maven.scm.ChangeSet;
import org.apache.maven.scm.ScmBranch;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmResult;
import org.apache.maven.scm.ScmRevision;
import org.apache.maven.scm.command.changelog.ChangeLogScmRequest;
import org.apache.maven.scm.command.changelog.ChangeLogScmResult;
import org.apache.maven.scm.command.changelog.ChangeLogSet;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.scm.provider.ScmProvider;
import org.apache.maven.scm.provider.ScmProviderRepository;
import org.apache.maven.scm.provider.ScmProviderRepositoryWithHost;
import org.apache.maven.scm.provider.svn.repository.SvnScmProviderRepository;
import org.apache.maven.scm.repository.ScmRepository;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.WriterFactory;


@Mojo(name = "changelog")
public class ChangeLog extends AbstractMojo {
    /**
     * Used to specify the date format of the log entries that are retrieved from
     * your SCM system.
     */
    @Parameter(property = "changelog.dateFormat", defaultValue = "yyyy-MM-dd HH:mm:ss", required = true)
    private String dateFormat;
    /**
     * Input dir. Directory where the files under SCM control are located.
     */
    @Parameter(property = "basedir", required = true)
    private File basedir;

    @Parameter(defaultValue = "${project.build.directory}", required = true)
    private String outFile;

    /**
     * The user name (used by svn and starteam protocol).
     */
    @Parameter(property = "username")
    private String username;

    /**
     * The user password (used by svn and starteam protocol).
     */
    @Parameter(property = "password")
    private String password;

    @Parameter(defaultValue = "${project.version}")
    private String  version;

    @Parameter( property = "changelog.type", defaultValue = "range", required = true )
    private String type;

    @Parameter( property = "changelog.range", defaultValue = "-1" )
    private int range;

    @Parameter
    private List<String> tags;

    @Parameter( property = "privateKey" )
    private String privateKey;

    @Parameter( property = "passphrase" )
    private String passphrase;

    /**
     * The url of tags base directory (used by svn protocol).
     */
    @Parameter( property = "tagBase" )
    private String tagBase;

    @Parameter(property = "startVersion", required = true)
    private long startVersion;
    @Parameter(property = "endVersion", required = true)
    private long endVersion;
    /**
     */
    @Component
    private ScmManager manager;

    // field for SCM Connection URL
    private String connection;

    /**
     * The Maven Project Object
     */
    @Component
    private MavenProject project;
    /**
     * Allows the user to choose which scm connection to use when connecting to the scm.
     * Can either be "connection" or "developerConnection".
     */
    @Parameter( defaultValue = "connection", required = true )
    private String connectionType;

    /**
     */
    @Component
    private Settings settings;

    /**
     * List of files to include. Specified as fileset patterns of files to include in the report
     *
     * @since 2.3
     */
    @Parameter
    private String[] includes;

    /**
     * List of files to include. Specified as fileset patterns of files to omit in the report
     *
     * @since 2.3
     */
    @Parameter
    private String[] excludes;


    /**
     * Used to specify the absolute date (or list of dates) to start log entries from.
     */
    @Parameter
    private List<String> dates;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {

            // String logs = new ModerOption().filterCommitHistory(startVersion,endVersion,version);
            List<ChangeLogSet> logs = generateChangeSetsFromSCM();
            File outputXML = new File(outFile + "/changelog.md");
            List<String> results = new ArrayList<>();
            logs.forEach((ChangeLogSet log)->{


                List<ChangeSet> changeSetsList =log.getChangeSets();
                changeSetsList.forEach((ChangeSet set)->{
                    String commentString =set.getComment();
                    results.add(commentString.replace("\n", "")+"["+set.getAuthor()+"]");

                });
            });
            String resultString = ModerOption.filterCommitHistory(version, results);
            Writer writer = WriterFactory.newWriter( new BufferedOutputStream( new FileOutputStream( outputXML ) ),"utf-8" );
            writer.write(resultString);
            writer.flush();
            writer.close();
        } catch (Exception e) {
            throw new MojoExecutionException("load changelog error", e);
        }
    }

    protected List<ChangeLogSet> generateChangeSetsFromSCM()
            throws MavenReportException
    {
        try
        {
            List<ChangeLogSet> changeSets = new ArrayList<ChangeLogSet>();

            ScmRepository repository = getScmRepository();

            ScmProvider provider = manager.getProviderByRepository( repository );

            ChangeLogScmResult result;

            if ( "range".equals( type ) )
            {
                result = provider.changeLog( repository, new ScmFileSet( basedir ), null, null, range, (ScmBranch) null,
                        dateFormat );

                checkResult( result );

                changeSets.add( result.getChangeLog() );
            }
            else if ( "tag".equals( type ) )
            {
                if ( repository.getProvider().equals( "svn" ) )
                {
                    throw new MavenReportException( "The type '" + type + "' isn't supported for svn." );
                }

                Iterator<String> tagsIter = tags.iterator();

                String startTag = tagsIter.next();
                String endTag = null;

                if ( tagsIter.hasNext() )
                {
                    while ( tagsIter.hasNext() )
                    {
                        endTag = tagsIter.next();

                        result = provider.changeLog( repository, new ScmFileSet( basedir ), new ScmRevision( startTag ),
                                new ScmRevision( endTag ) );

                        checkResult( result );

                        changeSets.add( result.getChangeLog() );

                        startTag = endTag;
                    }
                }
                else
                {
                    result = provider.changeLog( repository, new ScmFileSet( basedir ), new ScmRevision( startTag ),
                            new ScmRevision( endTag ) );

                    checkResult( result );

                    changeSets.add( result.getChangeLog() );
                }
            }
            else if ( "date".equals( type ) )
            {
                Iterator<String> dateIter = dates.iterator();

                String startDate = dateIter.next();
                String endDate = null;

                if ( dateIter.hasNext() )
                {
                    while ( dateIter.hasNext() )
                    {
                        endDate = dateIter.next();

                        result = provider.changeLog( repository, new ScmFileSet( basedir ), parseDate( startDate ),
                                parseDate( endDate ), 0, (ScmBranch) null );

                        checkResult( result );

                        changeSets.add( result.getChangeLog() );

                        startDate = endDate;
                    }
                }
                else
                {
                    result = provider.changeLog( repository, new ScmFileSet( basedir ), parseDate( startDate ),
                            parseDate( endDate ), 0, (ScmBranch) null );

                    checkResult( result );

                    changeSets.add( result.getChangeLog() );
                }
            }
            else if("version".equals(type)) {
                ChangeLogScmRequest request = new  ChangeLogScmRequest(repository,new ScmFileSet( basedir ));
                request.setStartRevision(new ScmRevision(startVersion+"") );
                request.setEndRevision(new ScmRevision(endVersion+"") );
                result = provider.changeLog(request);
                checkResult( result );

                changeSets.add( result.getChangeLog() );
            }
            else
            {
                throw new MavenReportException( "The type '" + type + "' isn't supported." );
            }
            filter( changeSets );
            return changeSets;

        }
        catch ( ScmException e )
        {
            throw new MavenReportException( "Cannot run changelog command : ", e );
        }
        catch ( MojoExecutionException e )
        {
            throw new MavenReportException( "An error has occurred during changelog command : ", e );
        }
    }

    /**
     * filters out unwanted files from the changesets
     */
    private void filter( List<ChangeLogSet> changeSets )
    {
        List<Pattern> include = compilePatterns( includes );
        List<Pattern> exclude = compilePatterns( excludes );
        if ( includes == null && excludes == null )
        {
            return;
        }
        for ( ChangeLogSet changeLogSet : changeSets )
        {
            List<ChangeSet> set = changeLogSet.getChangeSets();
            filter( set, include, exclude );
        }

    }
    private List<Pattern> compilePatterns( String[] patternArray )
    {
        if ( patternArray == null )
        {
            return new ArrayList<Pattern>();
        }
        List<Pattern> patterns = new ArrayList<Pattern>( patternArray.length );
        for ( String string : patternArray )
        {
            //replaces * with [/\]* (everything but file seperators)
            //replaces ** with .*
            //quotes the rest of the string
            string = "\\Q" + string + "\\E";
            string = string.replace( "**", "\\E.?REPLACEMENT?\\Q" );
            string = string.replace( "*", "\\E[^/\\\\]?REPLACEMENT?\\Q" );
            string = string.replace( "?REPLACEMENT?", "*" );
            string = string.replace( "\\Q\\E", "" );
            patterns.add( Pattern.compile( string ) );
        }
        return patterns;
    }

    private void filter( List<ChangeSet> sets, List<Pattern> includes, List<Pattern> excludes )
    {
        Iterator<ChangeSet> it = sets.iterator();
        while ( it.hasNext() )
        {
            ChangeSet changeSet = it.next();
            List<ChangeFile> files = changeSet.getFiles();
            Iterator<ChangeFile> iterator = files.iterator();
            while ( iterator.hasNext() )
            {
                ChangeFile changeFile = iterator.next();
                String name = changeFile.getName();
                if ( !isIncluded( includes, name ) || isExcluded( excludes, name ) )
                {
                    iterator.remove();
                }
            }
            if ( files.isEmpty() )
            {
                it.remove();
            }
        }
    }

    private boolean isExcluded( List<Pattern> excludes, String name )
    {
        if ( excludes == null || excludes.isEmpty() )
        {
            return false;
        }
        for ( Pattern pattern : excludes )
        {
            if ( pattern.matcher( name ).matches() )
            {
                return true;
            }
        }
        return false;
    }

    private boolean isIncluded( List<Pattern> includes, String name )
    {
        if ( includes == null || includes.isEmpty() )
        {
            return true;
        }
        for ( Pattern pattern : includes )
        {
            if ( pattern.matcher( name ).matches() )
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts the localized date string pattern to date object.
     *
     * @return A date
     */
    private Date parseDate( String date )
            throws MojoExecutionException
    {
        if ( date == null || date.trim().length() == 0 )
        {
            return null;
        }

        SimpleDateFormat formatter = new SimpleDateFormat( "yyyy-MM-dd" );

        try
        {
            return formatter.parse( date );
        }
        catch ( ParseException e )
        {
            throw new MojoExecutionException( "Please use this date pattern: " + formatter.toLocalizedPattern(), e );
        }
    }


    public ScmRepository getScmRepository()
            throws ScmException
    {
        ScmRepository repository;

        try
        {
            repository = manager.makeScmRepository( getConnection() );

            ScmProviderRepository providerRepo = repository.getProviderRepository();

            if ( !StringUtils.isEmpty( username ) )
            {
                providerRepo.setUser( username );
            }

            if ( !StringUtils.isEmpty( password ) )
            {
                providerRepo.setPassword( password );
            }

            if ( repository.getProviderRepository() instanceof ScmProviderRepositoryWithHost )
            {
                ScmProviderRepositoryWithHost repo = (ScmProviderRepositoryWithHost) repository.getProviderRepository();

                loadInfosFromSettings( repo );

                if ( !StringUtils.isEmpty( username ) )
                {
                    repo.setUser( username );
                }

                if ( !StringUtils.isEmpty( password ) )
                {
                    repo.setPassword( password );
                }

                if ( !StringUtils.isEmpty( privateKey ) )
                {
                    repo.setPrivateKey( privateKey );
                }

                if ( !StringUtils.isEmpty( passphrase ) )
                {
                    repo.setPassphrase( passphrase );
                }
            }

            if ( !StringUtils.isEmpty( tagBase ) && repository.getProvider().equals( "svn" ) )
            {
                SvnScmProviderRepository svnRepo = (SvnScmProviderRepository) repository.getProviderRepository();

                svnRepo.setTagBase( tagBase );
            }
        }
        catch ( Exception e )
        {
            throw new ScmException( "Can't load the scm provider.", e );
        }

        return repository;
    }

    /**
     * used to retrieve the SCM connection string
     *
     * @return the url string used to connect to the SCM
     * @throws MavenReportException when there is insufficient information to retrieve the SCM connection string
     */
    protected String getConnection()
            throws MavenReportException
    {
        if ( this.connection != null )
        {
            return connection;
        }

        if ( project.getScm() == null )
        {
            throw new MavenReportException( "SCM Connection is not set." );
        }

        String scmConnection = project.getScm().getConnection();
        if ( StringUtils.isNotEmpty( scmConnection ) && "connection".equals( connectionType.toLowerCase() ) )
        {
            connection = scmConnection;
        }

        String scmDeveloper = project.getScm().getDeveloperConnection();
        if ( StringUtils.isNotEmpty( scmDeveloper ) && "developerconnection".equals( connectionType.toLowerCase() ) )
        {
            connection = scmDeveloper;
        }

        if ( StringUtils.isEmpty( connection ) )
        {
            throw new MavenReportException( "SCM Connection is not set." );
        }

        return connection;
    }

    /**
     * Load username password from settings if user has not set them in JVM properties
     *
     * @param repo
     */
    private void loadInfosFromSettings( ScmProviderRepositoryWithHost repo )
    {
        if ( username == null || password == null )
        {
            String host = repo.getHost();

            int port = repo.getPort();

            if ( port > 0 )
            {
                host += ":" + port;
            }

            Server server = this.settings.getServer( host );

            if ( server != null )
            {
                if ( username == null )
                {
                    username = this.settings.getServer( host ).getUsername();
                }

                if ( password == null )
                {
                    password = this.settings.getServer( host ).getPassword();
                }

                if ( privateKey == null )
                {
                    privateKey = this.settings.getServer( host ).getPrivateKey();
                }

                if ( passphrase == null )
                {
                    passphrase = this.settings.getServer( host ).getPassphrase();
                }
            }
        }
    }

    public void checkResult( ScmResult result )
            throws MojoExecutionException
    {
        if ( !result.isSuccess() )
        {
            getLog().error( "Provider message:" );

            getLog().error( result.getProviderMessage() == null ? "" : result.getProviderMessage() );

            getLog().error( "Command output:" );

            getLog().error( result.getCommandOutput() == null ? "" : result.getCommandOutput() );

            throw new MojoExecutionException( "Command failed." );
        }
    }

}
