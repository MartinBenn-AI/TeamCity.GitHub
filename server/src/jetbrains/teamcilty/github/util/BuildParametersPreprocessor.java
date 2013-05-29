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

import java.util.Map;

public class BuildParametersPreprocessor implements ParametersPreprocessor {
  private static final Logger LOG = LoggerHelper.getInstance(ChangeStatusListener.class);
  public static final String SYSTEM_PULL_REQUEST_DESTINATION_BRANCH = "system.PullRequestDestinationBranch";

  private boolean changedValue = false;
  private long buildId;

  BuildParametersPreprocessor() {
    LOG.debug("BuildParametersPreprocessor initialized.");
  }

  public void fixRunBuildParameters(SRunningBuild build, Map<String, String> runParameters, Map<String, String> buildParams) {
    LOG.debug("BuildParametersPreprocessor asked for properties for build id " + build.getBuildId());

    if (buildId != build.getBuildId()) {
       changedValue = false;
    }

    if (!changedValue) {
      SBuildType buildType = build.getBuildType();
      for (SBuildFeatureDescriptor feature : buildType.getBuildFeatures()) {
        if (feature.getBuildFeature().getType().equals(UpdateChangeStatusFeature.FEATURE_TYPE)) {
          String gitBranchBaseLabel = GitBranchBaseLabel(build, buildParams, feature);
          if (gitBranchBaseLabel != null) {
            buildParams.put(SYSTEM_PULL_REQUEST_DESTINATION_BRANCH, gitBranchBaseLabel);
            changedValue = true;
            buildId = build.getBuildId();
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

    String branchSpec = buildParams.get("teamcity.build.vcs.branch." + vcsName);
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

      if (pullRequestInfo.base != null) {
        if (pullRequestInfo.base.ref != "") {
          return pullRequestInfo.base.ref;
        }
      }
    } catch (Exception e) {
      LOG.error("Failed to get Github Status with message: ", e);
    }
    return null;
  }
}

