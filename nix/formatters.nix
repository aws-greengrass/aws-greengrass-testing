{ llvmPackages, cmake-format, yapf, nodePackages, ... }:
let
  fmt-yaml = "${nodePackages.prettier}/bin/prettier --write --parser yaml";
in
{
  "*.py" = "${yapf}/bin/yapf -i";
  "*.yaml" = fmt-yaml;
  "*.yml" = fmt-yaml;
}
