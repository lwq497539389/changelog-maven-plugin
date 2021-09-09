package com.xyhsoft.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.scm.ScmException;

public class MavenReportException extends Exception {
    public MavenReportException(String s, MojoExecutionException e) {
    }

    public MavenReportException(String msg) {
        super(msg);
    }

    public MavenReportException(String msg, ScmException e) {
        super(msg,e);
    }
}
