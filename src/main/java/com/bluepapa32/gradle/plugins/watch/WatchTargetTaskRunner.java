package com.bluepapa32.gradle.plugins.watch;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.gradle.api.Project;
import org.gradle.api.logging.LogLevel;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProgressEvent;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.ResultHandler;

import static org.gradle.api.logging.LogLevel.INFO;

public class WatchTargetTaskRunner implements AutoCloseable {

    private final Map<String,?> properties;

    private ProjectConnection connection;

    public WatchTargetTaskRunner(Project project) {
        properties = project.getProperties();

        final PrintStream out = System.out;

        LogLevel logLevel = project.getGradle().getStartParameter().getLogLevel();
        if (INFO.compareTo(logLevel) < 0) {
            System.setOut(new DevNullPrintStream());
        }

        out.print("Starting");
        out.flush();

        connection = GradleConnector.newConnector()
                .useInstallation(project.getGradle().getGradleHomeDir())
                .forProjectDirectory(project.getProjectDir())
                .connect();

        connection
            .newBuild()
            .withArguments("-q")
            .addProgressListener(new ProgressListener() {
                public void statusChanged(ProgressEvent event) {
                    out.print("..");
                    out.flush();
                }
            })
            .run(new ResultHandler<Void>() {
                public void onComplete(Void result) {
                    System.setOut(out);
                    out.println(" OK");
                }
                public void onFailure(GradleConnectionException failure) {
                }
            });
    }

    public void run(List<WatchTarget> targets) {

        if (targets == null || targets.isEmpty()) {
            return;
        }

        BuildLauncher launcher = connection.newBuild();

        List<String> arguments = new ArrayList<>();
        for (Map.Entry<String, ?> entry : properties.entrySet()) {
            if (entry.getValue() instanceof String) {
                arguments.add("-P" + entry.getKey() + "=" + entry.getValue());
            }
        }
        launcher.withArguments(arguments.toArray(new String[arguments.size()]));

        for (WatchTarget target : targets) {
            launcher.forTasks(target.getTasks().toArray(new String[0]));
        }

        final int[] taskNum = new int[1];

        launcher.addProgressListener(new ProgressListener() {
            public void statusChanged(ProgressEvent event) {
                if ("Execute tasks".equals(event.getDescription())) {
                    taskNum[0]++;
                }
            }
        });

        long timestamp = System.currentTimeMillis();

        try {

            launcher.run();

        } catch (BuildException e) {
            // ignore...
        }

        for (WatchTarget target : targets) {
            target.setExecutedAt(timestamp);
        }
    }

    public void close() {
        connection.close();
    }
}
