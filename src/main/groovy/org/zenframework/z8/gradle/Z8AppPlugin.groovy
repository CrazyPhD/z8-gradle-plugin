package org.zenframework.z8.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySubstitution
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.tasks.Copy

class Z8AppPlugin implements Plugin<Project> {

	void apply(Project project) {
		if (!project.hasProperty('z8Version'))
			project.ext.z8Version = Z8Constants.Z8_DEFAULT_VERSION

		project.pluginManager.apply(ApplicationPlugin.class);

		project.configurations {
			boot
			resources {
				canBeResolved = true
				canBeConsumed = false
				attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
						project.objects.named(LibraryElements, 'web'))
			}
		}

		project.allprojects {
			configurations.all {
				resolutionStrategy.dependencySubstitution.all { DependencySubstitution dependency ->
					if (dependency.requested instanceof ModuleComponentSelector && dependency.requested.group == project.group) {
						def targetProject = findProject(":${dependency.requested.module}")
						if (targetProject != null) {
							println ">>>>>>>>>>>>> Substitute ${dependency.requested.displayName} by ${targetProject}"
							dependency.useTarget targetProject
						}
					}
				}
			}
		}

		project.dependencies {
			boot "org.zenframework.z8:org.zenframework.z8.boot:${project.z8Version}"
			resources "org.zenframework.z8:org.zenframework.z8.resources:${project.z8Version}@zip"
		}

		project.tasks.register('prepareWeb', Copy) {
			description 'Prepare WEB resources'

			if (project.hasProperty('z8Home'))
				dependsOn project.gradle.includedBuild(project.file(project.z8Home).name).task(':org.zenframework.z8.resources:assembleZip')
			from (project.configurations.resources.inject(project.files()) { tree, zip ->
				tree.plus(project.zipTree(zip))
			}) {
				include 'web/css/**'
				include 'web/WEB-INF/fonts/**'
				include 'web/WEB-INF/reports/**'
				include 'web/WEB-INF/resources/**'
			}
			from('src') {
				include 'web/**/*'
				filesMatching(['web/**/*.html', 'web/WEB-INF/project.xml']) {
					expand project: project
				}
			}
			into project.buildDir
		}

		project.tasks.register('prepareDebug', Copy) {
			description 'Prepare WEB debug resources'
			dependsOn project.tasks.prepareWeb
		
			from ("${project.buildDir}/web") {
				include 'css/fonts/**'
			}
			from('src/web') {
				include 'index.html'
			}
			into "${project.buildDir}/web/debug"
		}

		project.tasks.register('assembleWeb', Copy) {
			group 'Build'
			description 'Assemble WEB resources'
			dependsOn project.tasks.minifyCss, project.tasks.minifyJs, project.tasks.prepareWeb, project.tasks.prepareDebug
		}

		project.tasks.assemble.dependsOn project.tasks.assembleWeb
		project.tasks.distZip.dependsOn project.tasks.assembleWeb
		project.tasks.distTar.dependsOn project.tasks.assembleWeb
		project.tasks.installDist.dependsOn project.tasks.assembleWeb

		project.pluginManager.withPlugin('eclipse') {
			project.eclipse {
				autoBuildTasks project.tasks.assembleWeb
			}
		}
	}

}
