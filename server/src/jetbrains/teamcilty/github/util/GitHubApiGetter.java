/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.teamcilty.github.util;

import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.teamcilty.github.api.GitHubApi;
import jetbrains.teamcilty.github.api.GitHubApiAuthenticationType;
import jetbrains.teamcilty.github.api.GitHubApiFactory;
import jetbrains.teamcilty.github.ui.UpdateChangesConstants;
import org.jetbrains.annotations.NotNull;

public class GitHubApiGetter {

  @NotNull
  public static GitHubApi getGitHubApi(@NotNull final SBuildFeatureDescriptor feature,
                                       @NotNull final UpdateChangesConstants updateChangesConstants,
                                       @NotNull final GitHubApiFactory gitHubApiFactory) {

    final String serverUrl = feature.getParameters().get(updateChangesConstants.getServerKey());
    if (serverUrl == null || StringUtil.isEmptyOrSpaces(serverUrl)) {
      throw new IllegalArgumentException("Failed to read GitHub URL from the feature settings");
    }

    final GitHubApiAuthenticationType authenticationType = GitHubApiAuthenticationType.parse(feature.getParameters().get(updateChangesConstants.getAuthenticationTypeKey()));
    switch (authenticationType) {
      case PASSWORD_AUTH:
        final String username = feature.getParameters().get(updateChangesConstants.getUserNameKey());
        final String password = feature.getParameters().get(updateChangesConstants.getPasswordKey());
        return gitHubApiFactory.openGitHubForUser(serverUrl, username, password);

      case TOKEN_AUTH:
        final String token = feature.getParameters().get(updateChangesConstants.getAccessTokenKey());
        return gitHubApiFactory.openGitHubForToken(serverUrl, token);

      default:
        throw new IllegalArgumentException("Failed to parse authentication type:" + authenticationType);
    }
  }
}
