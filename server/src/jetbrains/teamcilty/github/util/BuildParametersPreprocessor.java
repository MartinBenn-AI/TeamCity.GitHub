package jetbrains.teamcilty.github.util;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.serverSide.*;
import jetbrains.teamcilty.github.ChangeStatusListener;
import jetbrains.teamcilty.github.api.GitHubApi;
import jetbrains.teamcilty.github.api.GitHubApiFactory;
import jetbrains.teamcilty.github.api.impl.GitHubApiFactoryImpl;
import jetbrains.teamcilty.github.api.impl.HttpClientWrapperImpl;
import jetbrains.teamcilty.github.api.impl.data.PullRequestInfo;
import jetbrains.teamcilty.github.ui.UpdateChangeStatusFeature;
import jetbrains.teamcilty.github.ui.UpdateChangesConstants;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class BuildParametersPreprocessor implements ParametersPreprocessor {
  private static final Logger LOG = LoggerHelper.getInstance(ChangeStatusListener.class);
  public static final String SYSTEM_PULL_REQUEST_DESTINATION_BRANCH = "system.PullRequestDestinationBranch";

  private boolean changedValue = false;
  private long buildId;

  BuildParametersPreprocessor() {
    LOG.debug("BuildParametersPreprocessor initialized.");
  }

  public void fixRunBuildParameters(@NotNull SRunningBuild build, @NotNull Map<String, String> runParameters, @NotNull Map<String, String> buildParams) {
    if (buildId != build.getBuildId()) {
       changedValue = false;
    }

    if (!changedValue) {
      LOG.info("Attempting to determine which git branch the build id '" + build.getBuildId() + "' is from.");

      SBuildType buildType = build.getBuildType();
      for (SBuildFeatureDescriptor feature : buildType.getBuildFeatures()) {
        if (feature.getBuildFeature().getType().equals(UpdateChangeStatusFeature.FEATURE_TYPE)) {
          String gitBranchBaseLabel = GitBranchBaseLabel(build, buildParams, feature);
          if (gitBranchBaseLabel != null) {
            LOG.info("Setting system.PullRequestDestinationBranch property to '" + gitBranchBaseLabel + "' for build id '" + build.getBuildId() + "'");
            buildParams.put(SYSTEM_PULL_REQUEST_DESTINATION_BRANCH, gitBranchBaseLabel);
            changedValue = true;
            buildId = build.getBuildId();
          }
          else {
            LOG.info("Failed to set system.PullRequestDestinationBranch property");
          }
        }
      }
    }
  }

  private String GitBranchBaseLabel(SBuild build, Map<String, String> buildParams, SBuildFeatureDescriptor feature) {
    String vcsName = "";
    for (int i = 0; i < build.getVcsRootEntries().size(); i++) {
      vcsName = build.getVcsRootEntries().get(i).getVcsRoot().getName();
    }
    String projectPrefex = buildParams.get("teamcity.project.id") + "_" + vcsName;

    LOG.debug("Attempting to get 'teamcity.build.vcs.branch." + projectPrefex + "'");

    String branchSpec = buildParams.get("teamcity.build.vcs.branch." + projectPrefex);
    LOG.debug("BranchSpec is " + branchSpec);

    if (branchSpec != null) {
      return getLabelFromGitHub(feature, branchSpec);
    }
    return null;
  }

  private String getLabelFromGitHub(SBuildFeatureDescriptor feature, String branchSpec) {
    try {
      HttpClientWrapperImpl clientWrapper = new HttpClientWrapperImpl();

      GitHubApiFactory myFactory = new GitHubApiFactoryImpl(clientWrapper);

      final UpdateChangesConstants c = new UpdateChangesConstants();

      final GitHubApi api = myFactory.openGitHub(
              feature.getParameters().get(c.getServerKey()),
              feature.getParameters().get(c.getUserNameKey()),
              feature.getParameters().get(c.getPasswordKey()));

      final String repositoryOwner = feature.getParameters().get(c.getRepositoryOwnerKey());
      final String repositoryName = feature.getParameters().get(c.getRepositoryNameKey());

      final PullRequestInfo pullRequestInfo = api.findPullRequestCommit(repositoryOwner, repositoryName, branchSpec);

      if (pullRequestInfo != null) {
        if (pullRequestInfo.base != null) {
          if (pullRequestInfo.base.ref != null) {
            if (!pullRequestInfo.base.ref.equals("")) {
              LOG.info("pullRequestInfo.base.ref is " + pullRequestInfo.base.ref);
              return pullRequestInfo.base.ref;
            }
          }
        }
      }
    } catch (Exception e) {
      LOG.error("Failed to get Github Status with message: ", e);
    }
    return null;
  }
}

