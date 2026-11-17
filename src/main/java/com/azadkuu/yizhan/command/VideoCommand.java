package com.azadkuu.yizhan.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** /video 与 /v 的入口，实际逻辑在 YizhanCommand。 */
public final class VideoCommand implements CommandExecutor {

    private final YizhanCommand delegate;

    public VideoCommand(YizhanCommand delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return delegate.video(sender, args);
    }
}
