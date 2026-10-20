package com.einsteinmods.relativity.benchmark.pack;

import java.util.List;

/**
 * Pinned optimization pack of the {@code pack} benchmark profile: the optimization mods of the
 * user's "Tecnico" instance (without Dynamic FPS and Distant Horizons) plus the 26.3 optimization
 * mods it lacks, and their hard dependencies that a dev run does not already provide (Fabric API,
 * MixinExtras and jar-in-jar libraries are). Versions resolved on 2026-10-18 through {@code GET
 * /v2/project/{slug}/version?loaders=["fabric"]&game_versions=["26.3"]}.
 */
record PackMod(
    String name, String versionNumber, String versionId, String fileName, String sha512) {
  static final List<PackMod> ALL =
      List.of(
          new PackMod(
              "Sodium",
              "mc26.3-0.9.2-fabric",
              "bAZQdGpg",
              "sodium-fabric-0.9.2+mc26.3.jar",
              "f3f260f204b8ce5e8777c2c73f537956755ec61f3817b0b78b5e5568e8aa40f14891f146475075109747665c7c88a45ca0ca9a8df3c68d79c58ecdaa8d8c4b93"),
          new PackMod(
              "Sodium Extra",
              "mc26.3-0.9.4+fabric",
              "te2y9qZn",
              "sodium-extra-fabric-0.9.4+mc26.3.jar",
              "a2e8d8416a556b7a351460a06407119eaba62f06636377001720bbda9b797dc881b2bb4528fb75a0459fee34475db529858633b6b86b545a72d0f13cf3c2b7bb"),
          new PackMod(
              "Lithium",
              "mc26.3-0.26.2-fabric",
              "xS0Q8LSi",
              "lithium-fabric-0.26.2+mc26.3.jar",
              "4d7fee66132eedc71feab9390b92c95d7058edbdad0fecfac1d836a2950b97a7ca463afbede61c7ef361ce65e1f927ab9e89dde5bf2e0e0ce486afb5c5dbee40"),
          new PackMod(
              "C2ME",
              "0.4.2-alpha.0.89+26.3",
              "FXjQDzq7",
              "c2me-fabric-mc26.3-0.4.2-alpha.0.89.jar",
              "b4fcf82bbe15bfa253e3ab6df4d6b64e800a2ccdb45d4ca2b8040da87f72033fed34f4f94016aac507596c78fddb18dba28c6642790181642cae46fae28b3a85"),
          new PackMod(
              "ScalableLux",
              "0.3.0-alpha.0.6+26.3",
              "g4eqNSKd",
              "ScalableLux-fabric-mc26.3-0.3.0-alpha.0.6-all.jar",
              "ded5a939fb20ab81c1f33b147ca9b98050773dbb28dff16df939c7d567b626809522994ec8dbfb99e7f498dc6a4e62f25e141a87d18efb43f73fc5ad1569ccc9"),
          new PackMod(
              "VMP",
              "0.2.0+beta.7.238+26.3",
              "2TFDVjDf",
              "vmp-fabric-mc26.3-0.2.0+beta.7.238-all.jar",
              "c8658ccf8818a258e1160a2d394c80fd52708611570dc28d6bc391671a23e193760766ef77ddc5907e1a3a757cf20f4def67ae2bc184269effa547da1f377667"),
          new PackMod(
              "FerriteCore",
              "9.0.0-fabric",
              "d5ddUdiB",
              "ferritecore-9.0.0-fabric.jar",
              "d81fa97e11784c19d42f89c2f433831d007603dd7193cee45fa177e4a6a9c52b384b198586e04a0f7f63cd996fed713322578bde9a8db57e1188854ae5cbe584"),
          new PackMod(
              "ModernFix",
              "5.27.20-build.2",
              "pa9cAfYg",
              "modernfix-5.27.20-build.2.jar",
              "b586434056107aac4ef86e9351d66170dac65a45a1e5f8bd48fb931b3012ab2431b16476eb9c256773339e2b4c60f6f3e90a4709180f700b2aea59ff607f4243"),
          new PackMod(
              "ImmediatelyFast",
              "1.17.1+26.3-fabric",
              "3MP9UR23",
              "ImmediatelyFast-Fabric-1.17.1+26.3.jar",
              "8e139589fd995bce3d485a34d92a07243964212f7ed2e4fc2f6de5b9a74d0f59b03252e7ce40c64e006cf3e21bed5de27b830d33ef048b286e041f6178ad8428"),
          new PackMod(
              "EntityCulling",
              "1.11.2",
              "F4loCvYt",
              "entityculling-fabric-1.11.2-mc26.3.jar",
              "c5d8947cb022f8baed4bf17e38ca598c56b8049871e7e47fb462bcecc9bc01e7eb75290494199fcb1aa73f5e37f93a296211ba78c9dd2a5bff30d5eed497ebcc"),
          new PackMod(
              "MoreCulling",
              "1.9.0",
              "t7vAlfgO",
              "moreculling-fabric-26.3-1.9.0.jar",
              "b1975ef43606a3baa1c93a040fbef96abfbf46ae6a1e364c60effd03a42568f945ede0bde329241e87adc6a9c2769dd1d13ad286869c689018035eff677442b2"),
          new PackMod(
              "BadOptimizations",
              "2.4.1",
              "Sp0ctspw",
              "BadOptimizations-2.4.1-26.3-fabric.jar",
              "048a0979807afb96bae7801b7445746c985081bb55fab9b80b286036234a9869cc84f83c40ab77a2600b7e06ad44178ee17935b706b751c9b38f87ebc6e180f8"),
          new PackMod(
              "AsyncParticles",
              "26.3.2.0-alpha.3",
              "iDHkUsnf",
              "AsyncParticles-26.3.2.0-alpha.3+26.3.jar",
              "d2d2e9d91656973fc203f3972de902bb82c5a834f2b1ee7aa3dea16676f309bd62b6ec5640280e77692abab56fad4c764767fe56c28e6c38a02cfebfb9232998"),
          new PackMod(
              "Clumps",
              "26.3.2",
              "J4I1wxJZ",
              "Clumps-fabric-26.3-26.3.2.jar",
              "8c166ae97e1999d0f213d0a181d6ef87b99648016c92c9aa1414d2c2e24364b12549569cd7e247a3d675df703ae6a8a88fc175d3f527818c710578408508c8ab"),
          new PackMod(
              "Better Block Entities",
              "1.4.0+mc26.3",
              "FU2tstk7",
              "bbe-fabric-1.4.0+mc26.3-mod.jar",
              "061404c7a546fcf97b1e95c84be6538634ef8a64c38c50ab52413bc30eb3ca631d70469ec9a5ec6e701bbfbbd9779d65a3a70e359e80d8c36449ff2a2d286cab"),
          new PackMod(
              "Krypton",
              "0.3.2",
              "UugdIYJw",
              "krypton-0.3.2.jar",
              "d1d57ebd41395b75b01f130cd9503eb8d208212424a399ff9f367f50be8fbc1c6472442b33c686e777c3148f6a609520e8fb732c755157c358cb207fd4d1123a"),
          new PackMod(
              "ServerCore",
              "1.5.20+26.3",
              "LCG1Bm84",
              "servercore-fabric-1.5.20+26.3.jar",
              "abe1f806ea587971faf7de826e18b07314a4a9e690f64a4c3a54e7f61c3e6f4792633b13a917252bd8de154cec39da6619110d83799cf533450c5b4cac7a8448"),
          new PackMod(
              "Cull Leaves",
              "4.1.2+26.3-fabric",
              "jqOVPlQ8",
              "cullleaves-fabric-4.1.2+26.3.jar",
              "8cc1c814dd2c293138cb4f8bedcac8bb531fab2056ed64da85363ad26edd4172691bb16b3735c443306ba8480d2000629ae6f6713d4a76807a3460a6ac38da80"),
          new PackMod(
              "Alternate Current",
              "mc26.3-1.9.0",
              "nSBWPz6x",
              "alternate-current-mc26.3-1.9.0.jar",
              "347fa8d0338a7e113c0cde70306d05037dbf9fc1e33c476159b4f5cf7b4fce8d3798d820ebf0e0dedd8450417802cb3a030bdcbecaf509961a83b2e43a469ad5"),
          new PackMod(
              "Cloth Config (required by MoreCulling)",
              "26.3.159+fabric",
              "fg2uyxOW",
              "cloth-config-fabric-26.3.159.jar",
              "8924e19d41845096724fa10f3717bc51964a0edbbbcad0ff1e6c6187a398b08f356e49579a657c2dc44a7f92e3850771f3ca70bf8c1f654de60425163b91dbb1"));
}
