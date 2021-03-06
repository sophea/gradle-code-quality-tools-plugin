package com.vanniktech.code.quality.tools

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.FindBugs
import org.gradle.api.plugins.quality.Pmd

class CodeQualityToolsPlugin implements Plugin<Project> {
    @Override
    void apply(final Project rootProject) {
        rootProject.extensions.create('codeQualityTools', CodeQualityToolsPluginExtension)
        rootProject.codeQualityTools.extensions.create('findbugs', CodeQualityToolsPluginExtension.Findbugs)
        rootProject.codeQualityTools.extensions.create('checkstyle', CodeQualityToolsPluginExtension.Checkstyle)
        rootProject.codeQualityTools.extensions.create('pmd', CodeQualityToolsPluginExtension.Pmd)
        rootProject.codeQualityTools.extensions.create('lint', CodeQualityToolsPluginExtension.Lint)

        rootProject.subprojects { subProject ->
            afterEvaluate {
                def extension = rootProject.codeQualityTools

                if (!shouldIgnore(subProject, extension)) {
                    // Reason for checking again in each add method: Unit Tests (they can't handle afterEvaluate properly)
                    addPmd(subProject, rootProject, extension)
                    addCheckstyle(subProject, rootProject, extension)
                    addFindbugs(subProject, rootProject, extension)
                    addLint(subProject, extension)
                }
            }
        }
    }

    protected static boolean addPmd(final Project subProject, final Project rootProject, final CodeQualityToolsPluginExtension extension) {
        if (!shouldIgnore(subProject, extension) && extension.pmd.enabled) {
            subProject.plugins.apply('pmd')

            subProject.pmd {
                toolVersion = extension.pmd.toolVersion
                ignoreFailures = extension.pmd.ignoreFailures != null ? extension.pmd.ignoreFailures : !extension.failEarly
                ruleSetFiles = subProject.files(rootProject.file(extension.pmd.ruleSetFile))
            }

            subProject.task('pmd', type: Pmd) {
                description = 'Run pmd'
                group = 'verification'

                ruleSets = []

                source = subProject.fileTree(extension.pmd.source)
                include extension.pmd.include
                exclude extension.pmd.exclude

                reports {
                    html.enabled = extension.htmlReports
                    xml.enabled = extension.xmlReports
                }
            }

            subProject.check.dependsOn 'pmd'

            return true
        }

        return false
    }

    protected static boolean addCheckstyle(final Project subProject, final Project rootProject, final CodeQualityToolsPluginExtension extension) {
        if (!shouldIgnore(subProject, extension) && extension.checkstyle.enabled) {
            subProject.plugins.apply('checkstyle')

            subProject.checkstyle {
                toolVersion = extension.checkstyle.toolVersion
                configFile rootProject.file(extension.checkstyle.configFile)
                ignoreFailures = extension.checkstyle.ignoreFailures != null ? extension.checkstyle.ignoreFailures : !extension.failEarly
                showViolations extension.checkstyle.showViolations != null ? extension.checkstyle.showViolations : extension.failEarly
            }

            subProject.task('checkstyle', type: Checkstyle) {
                description = 'Run checkstyle'
                group = 'verification'

                source = subProject.fileTree(extension.checkstyle.source)
                include extension.checkstyle.include
                exclude extension.checkstyle.exclude

                classpath = subProject.files()

                reports {
                    html.enabled = extension.htmlReports
                    xml.enabled = extension.xmlReports
                }
            }

            subProject.check.dependsOn 'checkstyle'

            return true
        }

        return false
    }

    protected static boolean addFindbugs(final Project subProject, final Project rootProject, final CodeQualityToolsPluginExtension extension) {
        if (!shouldIgnore(subProject, extension) && extension.findbugs.enabled) {
            final String findbugsClassesPath = isAndroidProject(subProject) ? 'build/intermediates/classes/debug/' : 'build/classes/main/'

            subProject.plugins.apply('findbugs')

            subProject.findbugs {
                sourceSets = []
                ignoreFailures = extension.findbugs.ignoreFailures != null ? extension.findbugs.ignoreFailures : !extension.failEarly
                toolVersion = extension.findbugs.toolVersion
                effort = extension.findbugs.effort
                reportLevel = extension.findbugs.reportLevel
                excludeFilter = rootProject.file(extension.findbugs.excludeFilter)
            }

            subProject.task('findbugs', type: FindBugs, dependsOn: 'assemble') {
                description = 'Run findbugs'
                group = 'verification'

                classes = subProject.fileTree(findbugsClassesPath)
                source = subProject.fileTree(extension.findbugs.source)
                classpath = subProject.files()

                reports {
                    html.enabled = extension.htmlReports
                    xml.enabled = extension.xmlReports
                }
            }

            subProject.check.dependsOn 'findbugs'

            return true
        }

        return false
    }

    protected static boolean addLint(final Project subProject, final CodeQualityToolsPluginExtension extension) {
        if (!shouldIgnore(subProject, extension) && extension.lint.enabled && isAndroidProject(subProject)) {
            subProject.android.lintOptions {
                warningsAsErrors extension.lint.warningsAsErrors != null ? extension.lint.warningsAsErrors : extension.failEarly
                abortOnError extension.lint.abortOnError != null ? extension.lint.abortOnError : extension.failEarly
            }

            if (extension.lint.textReport != null) {
                subProject.android.lintOptions {
                    textReport extension.lint.textReport
                    textOutput extension.lint.textOutput
                }
            }

            subProject.check.dependsOn 'lint'

            return true
        }

        return false
    }

    protected static boolean isAndroidProject(final Project project) {
        final boolean isAndroidLibrary = project.plugins.hasPlugin('com.android.library')
        final boolean isAndroidApp = project.plugins.hasPlugin('com.android.application')
        return isAndroidLibrary || isAndroidApp
    }

    private static boolean shouldIgnore(final Project project, final CodeQualityToolsPluginExtension extension) {
        return extension.ignoreProjects?.contains(project.name)
    }
}
