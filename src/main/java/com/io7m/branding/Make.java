/*
 * Copyright © 2022 Mark Raynsford <code@io7m.com> https://www.io7m.com
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package com.io7m.branding;

import net.ifok.image.image4j.codec.ico.ICOEncoder;
import net.ifok.image.image4j.codec.ico.ICOImage;
import net.sf.saxon.Transform;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Branding image generator.
 */

public final class Make
{
  private static final Logger LOG =
    Logger.getLogger("com.io7m.branding.Make");

  private Make()
  {

  }

  private record Book(
    String id,
    String cover,
    String title)
  {
    private Book
    {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(cover, "cover");
      Objects.requireNonNull(title, "title");
    }
  }

  private record Project(
    String name,
    String description,
    URI imageSource,
    URI url,
    List<Book> books)
  {
    private Project
    {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(description, "description");
      Objects.requireNonNull(imageSource, "imageSource");
      Objects.requireNonNull(url, "url");
      Objects.requireNonNull(books, "books");
    }

    private static List<String> list(
      final Path file,
      final Properties p,
      final String name)
    {
      return Optional.ofNullable(p.getProperty(name))
        .map(s -> List.of(s.split("\s+")))
        .orElse(List.of());
    }

    private static String require(
      final Path file,
      final Properties p,
      final String name)
    {
      return Optional.ofNullable(p.getProperty(name))
        .orElseThrow(() -> {
          return new IllegalArgumentException(
            "%s: Missing required property: %s".formatted(file, name)
          );
        });
    }

    private static URI requireURI(
      final Path file,
      final Properties p,
      final String name)
    {
      return Optional.ofNullable(p.getProperty(name))
        .map(URI::create)
        .orElseThrow(() -> {
          return new IllegalArgumentException(
            "%s: Missing required property: %s".formatted(file, name)
          );
        });
    }

    static Project load(
      final String name,
      final Path file)
      throws IOException
    {
      final var props = new Properties();
      try (var stream = Files.newInputStream(file)) {
        props.loadFromXML(stream);
      } catch (final InvalidPropertiesFormatException e) {
        throw new IOException("Failed to parse %s".formatted(file), e);
      }

      final var bookIds =
        list(file, props, "books");
      final var books =
        new ArrayList<Book>(bookIds.size());

      for (final var bookId : bookIds) {
        books.add(
          new Book(
            bookId,
            require(file, props, "books.%s.cover".formatted(bookId)),
            require(file, props, "books.%s.title".formatted(bookId))
          )
        );
      }

      return new Project(
        name,
        require(file, props, "description"),
        requireURI(file, props, "source"),
        requireURI(file, props, "url"),
        books
      );
    }
  }

  private static final class TaskOne implements Runnable
  {
    private final String projectName;
    private final CompletableFuture<Void> future;
    private Path dirSource;
    private Path dirOutput;
    private Path file;
    private Path srcBackground;
    private Path outBackground;
    private Project project;
    private Path outBackgroundJPEG;
    private Path outBackgroundSVG;
    private Path outBackgroundGen;
    private Path srcIcon;
    private Path outIcon32;
    private Path outIcon64;
    private Path outIcon128;
    private Path srcEmblem;
    private Path outIcon48;
    private Path outIcon16;
    private Path outIco;

    private TaskOne(
      final String inProject,
      final CompletableFuture<Void> inFuture)
    {
      this.projectName =
        Objects.requireNonNull(inProject, "project");
      this.future =
        Objects.requireNonNull(inFuture, "inFuture");
    }

    private void info(
      final String format,
      final Object... args)
    {
      LOG.info("%s: %s".formatted(
        this.projectName,
        String.format(format, args)));
    }

    private void error(
      final String format,
      final Object... args)
    {
      LOG.severe("%s: %s".formatted(
        this.projectName,
        String.format(format, args)));
    }

    @Override
    public void run()
    {
      try {
        this.info("start");
        this.execute();
        this.info("completed");
        this.future.complete(null);
      } catch (final Throwable ex) {
        this.info("failed: " + ex);
        this.future.completeExceptionally(ex);
      }
    }

    private void execute()
      throws Exception
    {
      this.dirSource =
        Paths.get("src")
          .resolve("projects")
          .resolve(this.projectName)
          .toAbsolutePath();
      this.dirOutput =
        Paths.get("output")
          .resolve(this.projectName)
          .toAbsolutePath();
      this.file =
        this.dirSource.resolve("project.xml").toAbsolutePath();

      this.srcEmblem =
        Paths.get("src").resolve("emblem18.png");
      this.srcIcon =
        this.dirSource.resolve("icon.png");
      this.srcBackground =
        this.dirSource.resolve("background.png");
      this.outBackground =
        this.dirOutput.resolve("background.png");
      this.outBackgroundGen =
        this.dirOutput.resolve("background_generated.png");
      this.outBackgroundJPEG =
        this.dirOutput.resolve("background.jpg");
      this.outBackgroundSVG =
        this.dirOutput.resolve("background.svg");

      this.outIco =
        this.dirOutput.resolve("icon.ico");
      this.outIcon16 =
        this.dirOutput.resolve("icon16.png");
      this.outIcon32 =
        this.dirOutput.resolve("icon32.png");
      this.outIcon48 =
        this.dirOutput.resolve("icon48.png");
      this.outIcon64 =
        this.dirOutput.resolve("icon64.png");
      this.outIcon128 =
        this.dirOutput.resolve("icon128.png");

      this.project =
        Project.load(this.projectName, this.file);

      this.info("create directory " + this.dirOutput);
      Files.createDirectories(this.dirOutput);

      this.generateSocialImage();
      for (final var book : this.project.books) {
        this.generateBookCover(book);
      }

      this.generateIcons();
    }

    private void generateIcon32()
      throws IOException
    {
      this.info("generating 32x32 icon");
      generateIconPNG(
        32,
        this.srcEmblem,
        this.srcIcon,
        this.outIcon32
      );
    }

    private void generateIcon64()
      throws IOException
    {
      this.info("generating 64x64 icon");
      generateIconPNG(
        64,
        this.srcEmblem,
        this.srcIcon,
        this.outIcon64
      );
    }

    private void generateIcon48()
      throws IOException
    {
      this.info("generating 48x48 icon");
      generateIconPNG(
        48,
        this.srcEmblem,
        this.srcIcon,
        this.outIcon48
      );
    }

    private void generateIcon128()
      throws IOException
    {
      this.info("generating 128x128 icon");
      generateIconPNG(
        128,
        this.srcEmblem,
        this.srcIcon,
        this.outIcon128
      );
    }

    private void generateIcons()
      throws IOException
    {
      this.info("generating icons");
      this.generateIcon32();
      this.generateIcon48();
      this.generateIcon64();
      this.generateIcon128();
      this.generateIcon16();

      this.generateIco();
    }

    private void generateIco()
      throws IOException
    {
      final var images =
        List.of(
          ImageIO.read(this.outIcon128.toFile()),
          ImageIO.read(this.outIcon48.toFile()),
          ImageIO.read(this.outIcon32.toFile()),
          ImageIO.read(this.outIcon16.toFile())
        );

      ICOEncoder.write(images, this.outIco.toFile());
    }

    private void generateIcon16()
      throws IOException
    {
      this.info("generating 16x16 icon");
      scaleDownIcon(
        16,
        this.outIcon32,
        this.outIcon16
      );
    }

    private static void scaleDownIcon(
      final int size,
      final Path srcIcon,
      final Path outIcon)
      throws IOException
    {
      final var image =
        new BufferedImage(size, size, TYPE_INT_ARGB);
      final var srcImage =
        ImageIO.read(srcIcon.toFile());

      final var gimage = image.createGraphics();
      gimage.drawImage(srcImage, 0, 0, size, size, null);
      gimage.dispose();

      ImageIO.write(image, "PNG", outIcon.toFile());
    }

    private static void generateIconPNG(
      final int size,
      final Path srcEmblem,
      final Path srcIcon,
      final Path outPNG)
      throws IOException
    {
      final var image =
        new BufferedImage(size, size, TYPE_INT_ARGB);
      final var srcImage =
        ImageIO.read(srcIcon.toFile());
      final var srcEmblemImage =
        ImageIO.read(srcEmblem.toFile());

      final var gimage = image.createGraphics();
      gimage.setRenderingHint(
        RenderingHints.KEY_RENDERING,
        RenderingHints.VALUE_RENDER_QUALITY);
      gimage.setRenderingHint(
        RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON);
      gimage.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION,
        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      gimage.drawImage(srcImage, 0, 0, size, size, null);
      gimage.dispose();

      final var gline0 = image.createGraphics();
      gline0.setColor(Color.BLACK);
      gline0.drawLine(0, 0, size, 0);
      gline0.drawLine(0, 0, 0, size);
      gline0.drawLine(0, size - 1, size - 1, size - 1);
      gline0.drawLine(size - 1, 0, size - 1, size - 1);
      gline0.dispose();

      final var gline1 = image.createGraphics();
      gline1.setColor(Color.WHITE);
      gline1.setComposite(AlphaComposite.getInstance(
        AlphaComposite.SRC_OVER,
        0.5f));
      gline1.drawLine(1, 1, size, 1);
      gline1.drawLine(1, 1, 1, size);
      gline1.drawLine(1, size - 2, size - 2, size - 2);
      gline1.drawLine(size - 2, 1, size - 2, size - 2);
      gline1.dispose();

      final var gemblem = image.createGraphics();
      gemblem.translate(size, size);
      gemblem.translate(-4, -4);
      gemblem.translate(-18, -18);
      gemblem.drawImage(srcEmblemImage, 0, 0, 18, 18, null);
      gemblem.dispose();

      ImageIO.write(image, "PNG", outPNG.toFile());
    }

    private void generateBookCover(
      final Book book)
      throws IOException, InterruptedException
    {
      this.info("generating book cover %s", book.id);

      final var srcBook =
        this.dirSource.resolve(book.cover);
      final var outBook =
        this.dirOutput.resolve("background.png");
      final var outSVG =
        this.dirOutput.resolve("cover.svg");
      final var outPNG =
        this.dirOutput.resolve("cover.png");
      final var outJPEG =
        this.dirOutput.resolve(book.id + ".jpeg");

      this.info("generating book cover %s", book.id);
      this.info("copy %s %s", srcBook, outBook);
      Files.copy(srcBook, outBook, REPLACE_EXISTING);

      this.generateBookCoverSVG(book, outSVG);
      this.generateBookCoverPNG(outSVG, outPNG);
      this.generateBookCoverJPEG(outPNG, outJPEG);

      Files.deleteIfExists(outBook);
      Files.deleteIfExists(outSVG);
      Files.deleteIfExists(outPNG);
    }

    private void generateBookCoverJPEG(
      final Path srcPNG,
      final Path outJPEG)
      throws IOException, InterruptedException
    {
      final var arguments =
        List.of(
          "convert",
          srcPNG.toString(),
          outJPEG.toString()
        );

      final var proc =
        new ProcessBuilder()
          .command(arguments)
          .directory(this.dirOutput.toFile())
          .redirectErrorStream(true)
          .start();

      final var readerThread = new Thread(() -> {
        try (var reader = new BufferedReader(
          new InputStreamReader(proc.getInputStream(), UTF_8)
        )) {
          while (proc.isAlive()) {
            final var line = reader.readLine();
            if (line == null) {
              return;
            }
            this.info("generateBookCoverJPEG: inkscape: %s", line);
          }
        } catch (final IOException e) {
          this.error("generateBookCoverJPEG: inkscape: %s", e);
        }
      });
      readerThread.start();

      final var exitCode = proc.waitFor();
      if (exitCode != 0) {
        this.error(
          "generateBookCoverJPEG: convert: exited with code %d",
          exitCode);
        throw new IOException("generateBookCoverJPEG: convert failed.");
      }
    }

    private void generateBookCoverPNG(
      final Path srcSVG,
      final Path outPNG)
      throws IOException, InterruptedException
    {
      final var arguments =
        List.of(
          "inkscape",
          "--export-type=png",
          "--export-width=600",
          "--export-height=800",
          "--export-filename=%s".formatted(outPNG),
          srcSVG.toString()
        );

      final var proc =
        new ProcessBuilder()
          .command(arguments)
          .directory(this.dirOutput.toFile())
          .redirectErrorStream(true)
          .start();

      final var readerThread = new Thread(() -> {
        try (var reader = new BufferedReader(
          new InputStreamReader(proc.getInputStream(), UTF_8)
        )) {
          while (proc.isAlive()) {
            final var line = reader.readLine();
            if (line == null) {
              return;
            }
            this.info("generateBookCoverPNG: inkscape: %s", line);
          }
        } catch (final IOException e) {
          this.error("generateBookCoverPNG: inkscape: %s", e);
        }
      });
      readerThread.start();

      final var exitCode = proc.waitFor();
      if (exitCode != 0) {
        this.error(
          "generateBookCoverPNG: inkscape: exited with code %d",
          exitCode);
        throw new IOException("generateBookCoverPNG: inkscape failed.");
      }
    }

    private void generateBookCoverSVG(
      final Book book,
      final Path outSVG)
    {
      final var arguments = new String[]{
        "-xsl:src/book_cover.xsl",
        "-s:src/book_cover.svg",
        "-o:%s".formatted(outSVG.toFile()),
        "projectName=%s".formatted(this.project.name),
        "projectDescription=%s".formatted(book.title),
      };

      Transform.main(arguments);
    }

    private void generateSocialImage()
      throws IOException, InterruptedException
    {
      this.info("generating social image");
      Files.copy(this.srcBackground, this.outBackground, REPLACE_EXISTING);
      this.generateSocialImageSVG();
      this.generateSocialImagePNG();
      this.generateSocialImageJPEG();
      Files.deleteIfExists(this.outBackgroundGen);
    }

    private void generateSocialImageJPEG()
      throws IOException, InterruptedException
    {
      final var arguments =
        List.of(
          "convert",
          this.outBackgroundGen.toString(),
          "background.jpg"
        );

      final var proc =
        new ProcessBuilder()
          .command(arguments)
          .directory(this.dirOutput.toFile())
          .redirectErrorStream(true)
          .start();

      final var readerThread = new Thread(() -> {
        try (var reader = new BufferedReader(
          new InputStreamReader(proc.getInputStream(), UTF_8)
        )) {
          while (proc.isAlive()) {
            final var line = reader.readLine();
            if (line == null) {
              return;
            }
            this.info("generateSocialImageJPEG: convert: %s", line);
          }
        } catch (final IOException e) {
          this.error("generateSocialImageJPEG: convert: %s", e);
        }
      });
      readerThread.start();

      final var exitCode = proc.waitFor();
      if (exitCode != 0) {
        this.error(
          "generateSocialImageJPEG: convert: exited with code %d",
          exitCode);
        throw new IOException("generateSocialImageJPEG: convert failed.");
      }
    }

    private void generateSocialImagePNG()
      throws IOException, InterruptedException
    {
      final var arguments =
        List.of(
          "inkscape",
          "--export-type=png",
          "--export-width=1280",
          "--export-height=640",
          "--export-filename=%s".formatted(this.outBackgroundGen),
          "background.svg"
        );

      final var proc =
        new ProcessBuilder()
          .command(arguments)
          .directory(this.dirOutput.toFile())
          .redirectErrorStream(true)
          .start();

      final var readerThread = new Thread(() -> {
        try (var reader = new BufferedReader(
          new InputStreamReader(proc.getInputStream(), UTF_8)
        )) {
          while (proc.isAlive()) {
            final var line = reader.readLine();
            if (line == null) {
              return;
            }
            this.info("generateSocialImagePNG: inkscape: %s", line);
          }
        } catch (final IOException e) {
          this.error("generateSocialImagePNG: inkscape: %s", e);
        }
      });
      readerThread.start();

      final var exitCode = proc.waitFor();
      if (exitCode != 0) {
        this.error(
          "generateSocialImagePNG: inkscape: exited with code %d",
          exitCode);
        throw new IOException("generateSocialImagePNG: inkscape failed.");
      }
    }

    private void generateSocialImageSVG()
    {
      final var arguments = new String[]{
        "-xsl:src/social3.xsl",
        "-s:src/social3.svg",
        "-o:%s".formatted(this.outBackgroundSVG.toFile()),
        "projectName=%s".formatted(this.project.name),
        "projectDescription=%s".formatted(this.project.description),
        "projectURL=%s".formatted(this.project.url),
      };

      Transform.main(arguments);
    }
  }

  /**
   * The main entry point.
   *
   * @param args Command-line arguments
   *
   * @throws Exception On errors
   */

  public static void main(
    final String[] args)
    throws Exception
  {
    System.setProperty(
      "java.util.logging.SimpleFormatter.format",
      "[%1$tF %1$tT] [%4$-7s] %5$s %n"
    );

    if (args.length != 2) {
      usage();
    }

    final var command = args[0];
    final var arg = args[1];

    final var executor =
      Executors.newFixedThreadPool(16, r -> {
        final var thread = new Thread(r);
        thread.setName("com.io7m.branding.task[%d]".formatted(thread.getId()));
        return thread;
      });

    switch (command) {
      case "one" -> {
        mainAll(executor, List.of(arg));
      }
      case "all" -> {
        try (var lines = Files.lines(Paths.get(arg))) {
          mainAll(executor, lines.toList());
        }
      }
      default -> usage();
    }
  }

  private static void mainAll(
    final ExecutorService executor,
    final List<String> projects)
    throws Exception
  {
    try {
      final var timeThen = Instant.now();
      final var futures = new CompletableFuture<?>[projects.size()];
      for (int index = 0; index < projects.size(); ++index) {
        final var project = projects.get(index);
        final var future = new CompletableFuture<Void>();
        futures[index] = future;
        executor.execute(new TaskOne(project, future));
      }

      var failed = 0;
      Exception exception = null;
      for (int index = 0; index < projects.size(); ++index) {
        try {
          futures[index].get();
        } catch (final Exception e) {
          ++failed;
          if (exception == null) {
            exception = e;
          } else {
            exception.addSuppressed(e);
          }
        }
      }

      final var timeNow = Instant.now();
      LOG.info("execution finished in %s".formatted(
        Duration.between(timeThen, timeNow)));
      LOG.info("executed %d projects".formatted(projects.size()));
      LOG.info("failed %d projects".formatted(failed));

      if (exception != null) {
        throw exception;
      }

    } finally {
      executor.shutdown();
    }
  }

  private static void usage()
  {
    System.err.println("usage: [one project] | [all projects.txt]");
    throw new IllegalArgumentException();
  }
}
