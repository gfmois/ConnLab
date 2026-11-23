// Gives the jpackage launcher the application's icon and version information, as jpackage
// does on Windows: node exe-resources.cjs <exe> <ico> <version> <name> <vendor>
// Needs the resedit package (NODE_PATH pointing at its node_modules).
const fs = require('node:fs');

async function main() {
  const [exePath, icoPath, version, name, vendor] = process.argv.slice(2);
  const { NtExecutable, NtExecutableResource, Data, Resource } = await require('resedit/cjs').load();

  const exe = NtExecutable.from(fs.readFileSync(exePath), { ignoreCert: true });
  const res = NtExecutableResource.from(exe);
  const lang = 1033;

  // Replace the launcher's own icon (every icon group it has) with the application's.
  const icons = Data.IconFile.from(fs.readFileSync(icoPath)).icons.map((i) => i.data);
  const groups = Resource.IconGroupEntry.fromEntries(res.entries);
  for (const g of groups.length ? groups : [{ id: 1, lang }]) {
    Resource.IconGroupEntry.replaceIconsForResource(res.entries, g.id, g.lang, icons);
  }

  const [major, minor = 0, micro = 0] = version.split(/[.-]/).map((n) => parseInt(n, 10) || 0);
  const vi = Resource.VersionInfo.fromEntries(res.entries)[0] ?? Resource.VersionInfo.createEmpty();
  vi.lang = lang;
  vi.setFileVersion(major, minor, micro, 0, lang);
  vi.setProductVersion(major, minor, micro, 0, lang);
  vi.setStringValues({ lang, codepage: 1200 }, {
    CompanyName: vendor,
    FileDescription: name,
    FileVersion: version,
    InternalName: name,
    LegalCopyright: `Copyright (C) ${vendor}`,
    OriginalFilename: `${name}.exe`,
    ProductName: name,
    ProductVersion: version,
  }, true);
  vi.outputToResourceEntries(res.entries);

  res.outputResource(exe);
  fs.writeFileSync(exePath, Buffer.from(exe.generate()));
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
