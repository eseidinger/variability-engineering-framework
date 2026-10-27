# Synthetic AI acceptance fixtures

The four loose JSON files are unmodified synthetic examples from Energy System
Configuration contract kit 0.5.0, whose release archive SHA-256 is
`ae4edaf6799d4b57510d14d2004c5b65ff6b05f3b225e96bbd5971b30ce8832b`.
They are repository-authored examples licensed CC-BY-4.0 under the kit's
`LICENSE` and `LICENSES/CC-BY-4.0.txt`, copied here solely for the private
AI-08/11 acceptance probe. The probe mutates in-memory copies only and does not
use site measurements, database credentials or an external model.

`ai10-fixtures.zip` contains the released 1.0.0 energy manifest, its five
artifacts, request and full optimization oracle, plus the approved synthetic
1.0.1 tariff correction with its manifest, five artifacts, request and
independently calculated oracle. The archive uses fixed ZIP timestamps and
entry order; SHA-256 is
`8fbb0cf6fb7d65da84be8ee554b5ae48abc808bd4cdb048787f99649d46e9d74`.
The original manifest SHA-256 is
`252b90b18b594a764a2744ba133a8db56b233e9e375301befc7616e9c18c1715`;
the corrected manifest SHA-256 is
`ebfd2cc7dcdf73cef4ed81a94b693ec23c4448460c8161ca648369ea0a930f93`.
The corrected import tariff increases each 2024 hourly value by EUR 0.01/kWh;
the original version and all other reference series remain unchanged.
PVGIS-derived source values retain the attribution and usage conditions in
`energy-contract/NOTICE`; the synthetic derived fixtures are covered by the
contract kit's file-scoped `LICENSE`. The AI-10 probe uses these bytes only in
memory and never contacts ODIP or changes a publication.
