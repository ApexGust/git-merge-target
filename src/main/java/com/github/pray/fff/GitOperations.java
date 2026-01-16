package com.github.pray.fff;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRemote;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class GitOperations {
    private static final Logger logger = LoggerFactory.getLogger(GitOperations.class);

    public static class GitCommandException extends Exception {
        public GitCommandException(String message) {
            super(message);
        }
    }

    /**
     * 合并分支
     */
    public static void mergeBranch(@NotNull Project project,
                                 @NotNull GitRepository repository,
                                 String sourceBranch,
                                 String targetBranch) throws GitCommandException {
        VirtualFile root = repository.getRoot();
        Git git = Git.getInstance();

        logger.info("开始合并分支: {} -> {} (Root: {})", sourceBranch, targetBranch, root.getPath());

        // 获取远程仓库名称（默认使用 origin，如果不存在则使用第一个远程）
        String remoteName = getRemoteName(repository);

        try {
            // 1. 切换到目标分支
            runGitCommand(project, root, GitCommand.CHECKOUT, "切换分支", targetBranch);

            // 2. 拉取代码
            runGitCommand(project, root, GitCommand.PULL, "拉取代码", remoteName, targetBranch);

            // 3. 合并
            runGitCommand(project, root, GitCommand.MERGE, "合并分支", "--no-ff", sourceBranch);

            // 4. 推送
            runGitCommand(project, root, GitCommand.PUSH, "推送代码", remoteName, targetBranch);

            // 5. 切回源分支（失败不抛异常，只记录）
            try {
                GitLineHandler checkoutBack = new GitLineHandler(project, root, GitCommand.CHECKOUT);
                checkoutBack.addParameters(sourceBranch);
                GitCommandResult result = git.runCommand(checkoutBack);
                if (!result.success()) {
                    logger.warn("切回源分支失败: {}", result.getErrorOutputAsJoinedString());
                }
            } catch (Exception e) {
                logger.warn("切回源分支时发生异常", e);
            }

            logger.info("合并完成: {} -> {}", sourceBranch, targetBranch);

        } catch (GitCommandException e) {
            throw e;
        } catch (Exception e) {
            logger.error("未预期的错误", e);
            throw new GitCommandException("内部错误: " + e.getMessage());
        }
    }

    /**
     * 获取远程仓库名称（优先 origin，否则使用第一个远程）
     */
    private static String getRemoteName(@NotNull GitRepository repository) {
        try {
            Collection<GitRemote> remotes = repository.getRemotes();
            if (remotes.isEmpty()) {
                return "origin"; // 默认值
            }
            // 优先使用 origin
            for (GitRemote remote : remotes) {
                if ("origin".equals(remote.getName())) {
                    return "origin";
                }
            }
            // 否则使用第一个远程
            return remotes.iterator().next().getName();
        } catch (Exception e) {
            logger.warn("无法获取远程仓库列表，使用默认值 origin", e);
            return "origin";
        }
    }

    /**
     * 提取通用执行逻辑，减少代码重复
     */
    private static void runGitCommand(Project project, VirtualFile root, GitCommand command, 
                                      String actionName, String... parameters) throws GitCommandException {
        GitLineHandler handler = new GitLineHandler(project, root, command);
        if (parameters != null && parameters.length > 0) {
            handler.addParameters(parameters);
        }

        GitCommandResult result = Git.getInstance().runCommand(handler);

        if (!result.success()) {
            String errorMsg = result.getErrorOutputAsJoinedString();
            logger.error("{} 失败: {}", actionName, errorMsg);
            throw new GitCommandException(actionName + " 失败: " + errorMsg);
        } else {
            logger.info("{} 成功", actionName);
        }
    }
}
