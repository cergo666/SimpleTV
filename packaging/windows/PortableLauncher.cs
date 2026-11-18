using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Security.Cryptography;
using System.Windows.Forms;

[assembly: AssemblyTitle("SimpleTV Portable")]
[assembly: AssemblyProduct("SimpleTV")]

internal static class PortableLauncher
{
    private const string AppName = "SimpleTV.exe";

    [STAThread]
    private static int Main()
    {
        try
        {
            using (var payload = Assembly.GetExecutingAssembly().GetManifestResourceStream("SimpleTV.zip"))
            {
                var directory = PrepareRuntime(payload);
                var startInfo = new ProcessStartInfo(Path.Combine(directory, AppName))
                {
                    WorkingDirectory = directory,
                    UseShellExecute = false,
                };
                using (Process.Start(startInfo)) { }
            }
            return 0;
        }
        catch (Exception error)
        {
            MessageBox.Show("Не удалось запустить SimpleTV.\n\n" + error.Message,
                "SimpleTV", MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }

    private static string PrepareRuntime(Stream payload)
    {
        if (payload == null)
            throw new InvalidDataException("В EXE отсутствуют файлы приложения.");

        var localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        if (string.IsNullOrEmpty(localAppData))
            throw new IOException("Папка LocalAppData недоступна.");
        var cache = Path.Combine(localAppData, "SimpleTV", "portable");
        string hash;
        using (var sha256 = SHA256.Create())
            hash = BitConverter.ToString(sha256.ComputeHash(payload)).Replace("-", "").ToLowerInvariant();

        var directory = Path.Combine(cache, hash);
        if (Directory.Exists(directory))
            return directory;

        // Publish the directory only after extraction finishes. A second
        // launcher can use the complete directory if it wins the rename.
        var staging = Path.Combine(cache, ".extract-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(staging);
        try
        {
            payload.Position = 0;
            using (var archive = new ZipArchive(payload, ZipArchiveMode.Read, true))
                archive.ExtractToDirectory(staging);

            if (!File.Exists(Path.Combine(staging, AppName)))
                throw new InvalidDataException("В пакете отсутствует SimpleTV.exe.");

            try
            {
                Directory.Move(staging, directory);
            }
            catch (IOException)
            {
                if (!Directory.Exists(directory)) throw;
            }
            return directory;
        }
        finally
        {
            if (Directory.Exists(staging))
                Directory.Delete(staging, true);
        }
    }
}
