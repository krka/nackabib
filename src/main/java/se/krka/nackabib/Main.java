package se.krka.nackabib;

import java.io.File;
import java.time.Duration;
import uk.co.flamingpenguin.jewel.cli.ArgumentValidationException;
import uk.co.flamingpenguin.jewel.cli.Cli;
import uk.co.flamingpenguin.jewel.cli.CliFactory;
import uk.co.flamingpenguin.jewel.cli.Option;

public class Main {

  public static final Duration MIN_WAIT_TIME = Duration.ofHours(4).minus(Duration.ofMinutes(15));

  public static void main(String[] args) throws Exception {
    final Cli<Options> cli = CliFactory.createCli(Options.class);
    try {
      final Options options = cli.parseArguments(args);
      final String dataDir = options.dataDir();
      if ("".equals(dataDir)) {
        System.err.println("dataDir must be specified");
        showHelp(cli);
        return;
      }
      final File baseDir = new File(dataDir);
      if (!baseDir.isDirectory()) {
        System.err.println(baseDir.getAbsolutePath() + " is not a directory");
        System.exit(1);
        return;
      }

      boolean canRender = true;
      final String renderFilename = options.render();
      if ("".equals(renderFilename)) {
        System.out.println("Skipping rendering, no render output specified");
        canRender = false;
      }
      final File renderFile = new File(renderFilename);
      final File parentFile = renderFile.getAbsoluteFile().getParentFile();
      if (!parentFile.isDirectory()) {
        System.err.println(parentFile.getAbsolutePath() + " is not a directory");
        System.exit(1);
        return;
      }
      if (renderFile.isDirectory()) {
        System.err.println(renderFile.getAbsolutePath() + " is a directory, cant render there");
        System.exit(1);
        return;
      }

      final Duration timeSinceLastUpdate = Util.timeSinceLastUpdate(baseDir);
      boolean shouldDownload = options.forceDownload() || timeSinceLastUpdate.compareTo(MIN_WAIT_TIME) > 0;

      if (shouldDownload) {
        Downloader.download(baseDir);
      } else {
        System.out.println("Already up to date, skipping download");
      }

      if (canRender && (options.forceRender() || shouldDownload)) {
        final Render render = new Render();
        render.collectData(baseDir);
        render.toHtml(renderFile);
        System.out.println("Done rendering to " + renderFile);
      }

    } catch (ArgumentValidationException e) {
      showHelp(cli);
    }
  }

  private static void showHelp(final Cli<Options> cli) {
    System.err.println(cli.getHelpMessage());
    System.exit(1);
  }

  public interface Options {
    @Option(
        longName = "help", shortName = "h",
        description = "Show help message",
        helpRequest = true
    )
    Void help();

    @Option(
        longName = "render", shortName = "r",
        description = "Render to file with name",
        defaultValue = ""
    )
    String render();

    @Option(
        longName = "force-download",
        description = "Always download, regardless of last download time"
    )
    boolean forceDownload();

    @Option(
        longName = "force-render",
        description = "Always render, regardless of download happened"
    )
    boolean forceRender();

    @Option(
        longName = "data-dir", shortName = "d",
        description = "Base directory for downloaded data",
        defaultValue = ""
    )
    String dataDir();

  }
}
