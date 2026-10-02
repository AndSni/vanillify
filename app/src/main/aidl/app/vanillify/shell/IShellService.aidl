package app.vanillify.shell;

// Runs inside Shizuku's shell-uid process (see ShellService).
interface IShellService {
    // Transaction code reserved by Shizuku for tearing the service down.
    void destroy() = 16777114;

    // stdout+stderr of `sh -c command`; null when it couldn't run or took longer than timeoutMs.
    String exec(String command, long timeoutMs) = 1;

    // ShellProtocol.VERSION of the running helper; an older helper answers 0.
    int protocol() = 2;
}
