pkgs:
let
  inherit (pkgs.inputs) uv2nix pyproject-nix pyproject-build-systems;
  inherit (pkgs) lib;

  workspace = uv2nix.lib.workspace.loadWorkspace { workspaceRoot = pkgs.src; };
  pythonSet = (pkgs.callPackage pyproject-nix.build.packages {
    python = pkgs.python3;
  }).overrideScope (lib.composeManyExtensions [
    pyproject-build-systems.overlays.default
    (workspace.mkPyprojectOverlay { sourcePreference = "wheel"; })
  ]);

  virtualenv = pythonSet.mkVirtualEnv "env" workspace.deps.all;
in
{
  packages = [
    pkgs.git
    pkgs.uv
    virtualenv
  ];
  env = {
    UV_NO_SYNC = "1";
    UV_PYTHON = "${virtualenv}/bin/python";
    UV_PYTHON_DOWNLOADS = "never";
  };
  shellHook = ''
    export MAKEFLAGS=-j
  '';
}
