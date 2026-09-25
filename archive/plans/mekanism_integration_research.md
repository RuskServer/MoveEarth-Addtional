# Mekanism統合調査・再設計案

## 0. 実装方針の決定

2026-09-20時点で、本資料の方向性をMoveEarthのMekanism統合方針として採用する。

電力Modは**Create: Electro Energetics（以下CEE）**へ固定する。Mekanismは独立した電力文明として導入せず、CEE電力網の先に接続する化学・原子力技術として扱う。

確定事項:

- 主電力網はCEEの発電機、配線、変圧器、保護設備、計器を使用する。
- Createの蒸気機関でCEEのAlternatorを回し、最初の大規模電力を得る。
- Mekanism機械はCEEのFE Converterを経由しなければ稼働できない構成にする。
- Mekanismの独立発電機はすべて削除する。
- MekanismのUniversal Cableは、FE Converter以降の工場内・原子力施設内配線として残す。
- CEEを長距離幹線、Universal Cableを構内FE配線として役割分担する。
- MekanismのEnergy Cubeは全Tier削除する。
- 原子力到達後の安全用大容量バッファとしてInduction Matrixだけを残す。
- Industrial TurbineおよびFusion Reactorの発電出力もFE Converterを介してCEE電力網へ戻す。
- CEE公式1.21.1ソース上でFE Converterの双方向変換を確認済み。採用バイナリでも同じ挙動を実機確認する。
- 原子力出力は単一Converterへ集中させず、複数Converterを並べた変電所でCEE系統へ連系する。
- アイテム物流はCreate、電力物流はCEE、液体・化学物質・熱物流はMekanismという役割分担にする。
- Mekanism本体とMekanism Generatorsは、この統合を有効にするMoveEarth配布構成では必須依存として扱う。
- Mekanism本体は`Mekanism-1.21.1-10.7.19.85`、Generatorsは`MekanismGenerators-1.21.1-10.7.19.85`へ固定する。`.85`は配布artifactのビルド番号であり、両modがNeoForgeへ公開するバージョンは`10.7.19`なので、依存メタデータは`[10.7.19]`とする。
- CEEも同じ配布構成で必須依存として扱い、欠落時に中途半端な進行を許可しない。
- 第一実装の完成地点は核分裂炉、Thermoelectric Boiler、Industrial Turbine、Induction Matrixまでとする。
- Fusion Reactor、SPS、Antimatterは第一実装には含めず、核分裂運用データ取得後に判断する。

役割分担:

| 分野 | 担当Mod・システム |
|---|---|
| 回転力・機械加工 | Create |
| 蒸気動力 | Create |
| 発電・送電・変圧・通常蓄電 | Create: Electro Energetics |
| FE境界 | CEE FE Converter |
| 工場・原子力施設内のFE分配 | Mekanism Universal Cable |
| 合金・制御回路 | Mekanism |
| 化学処理・気体処理 | Mekanism |
| 液体・化学物質・熱の配管 | Mekanism |
| 通常アイテム物流 | Create |
| 原子力発電 | Mekanism Generators |
| 原子力用大容量バッファ | Mekanism Induction Matrix |

この決定により、Mekanism導入後もCEEが送電インフラとして残り、Createの工場と物流も置き換えられない。

## 1. 目的

Mekanismを単独の工業Modとして追加するのではなく、MoveEarthの既存工業を次の一本の進行へ接続する。

```text
Create機械加工
  ↓
Create蒸気機関
  ↓
Create: Electro Energeticsによる発電・送電
  ↓
FE変換設備
  ↓
Mekanism基礎機械・合金・制御回路
  ↓
Mekanism化学処理
  ↓
核分裂燃料製造
  ↓
核分裂炉・産業用タービン
  ↓
核融合炉
```

狙いは、Create系の機械・蒸気・電力網を途中で捨てず、本格的な化学処理と原子力をMekanismで補完することである。

## 2. 調査基準

- Minecraft: 1.21.1
- MoveEarth側NeoForge: 21.1.238
- 調査したMekanism: `v1.21.1-10.7.19.85`
- Mekanism側NeoForge要求: `[21.1.194,)`
- Java: 21

MoveEarthの現在のNeoForgeバージョンはMekanismの要求範囲を満たしている。

調査には次を使用した。

- [Mekanism公式リリース](https://github.com/mekanism/Mekanism/releases)
- [Mekanism公式Wiki](https://wiki.aidancbrady.com/wiki/Mekanism)
- [Mekanism Generators公式Wiki](https://wiki.aidancbrady.com/wiki/Mekanism%3A_Generators)
- [Create: Electro Energetics公式README](https://github.com/george8188625/Create-Electro-Energetics/blob/1.21.1/README.md)
- Mekanism公式タグ `v1.21.1-10.7.19.85` のソースおよび生成済みレシピ

## 3. 導入モジュール

### 導入する

- Mekanism本体
- Mekanism Generators

Mekanism Generatorsは独立モジュールだが、核分裂炉、核融合炉、産業用タービンと独立発電機を収録している。核分裂・核融合を目的とする以上、Generatorsは必須となる。

### 導入しない

- Mekanism Tools
- Mekanism Additions

ToolsとAdditionsは今回の原子力・化学工業チェーンに不要であり、装備や周辺要素を増やして調整範囲を広げるため導入しない。

ただしMekaSuit、Meka-Tool、Atomic Disassembler、Jetpackなど一部の強力装備はMekanism本体側に含まれる。このため、Toolsを導入しないだけでは無効化できず、個別にレシピを削除する必要がある。

## 4. 標準Mekanismをそのまま導入できない理由

標準MekanismではHeat Generatorから冶金注入機を動かし、Mekanismだけで序盤を開始できる。

その後も風力、太陽光、バイオ燃料、エチレン式ガス発電へ進めるため、Create蒸気機関とElectro Energetics電力網を無視できる。特にGas-Burning Generatorは非常に強く、Mekanism公式の核分裂炉チュートリアルでも、小型核分裂炉の出力がエチレン式ガス発電の約2.5分の1になる例が示されている。

また、Mekanismには次のようなMoveEarthの中核システムと競合する機能がある。

- 最大5倍の鉱石処理
- 障害物越しに鉱石を回収するDigital Miner
- 遠隔・未ロード状態で利用できるQIO収納
- 長距離・次元間Teleport
- 無線で物資とエネルギーを転送するQuantum Entangloporter
- 高速万能工具、飛行装備、高耐久装備
- 1ブロックへ大量処理能力を集約するFactory

これらを残すと、地域資源、国家領土、収納制限、鉄道・車両輸送、Siege、Create工場建築をMekanismが置き換えてしまう。

## 5. 完全に削除する機能

「完全に削除」は、原則としてクラフトレシピを削除し、通常プレイでは入手不能にすることを意味する。必要に応じてJEI/EMI上のカテゴリや用途表示も整理する。

### 5.1 独立発電機

以下はすべて削除する。

- `mekanismgenerators:generator/heat`
- `mekanismgenerators:generator/solar`
- `mekanismgenerators:generator/advanced_solar`
- `mekanismgenerators:generator/wind`
- `mekanismgenerators:generator/bio`
- `mekanismgenerators:generator/gas_burning`

これにより、最初のMekanism機械を動かす電力はCreate: Electro Energeticsの電力網とFE変換設備から供給しなければならなくなる。

核分裂炉、核融合炉、産業用タービンは残す。

### 5.2 資源・領土・物流を破壊する設備

以下を削除する。

- `mekanism:digital_miner`
- `mekanism:cardboard_box`
- `mekanism:teleporter`
- `mekanism:portable_teleporter`
- `mekanism:teleporter_frame`
- `mekanism:teleportation_core`
- `mekanism:module_teleportation_unit`
- `mekanism:quantum_entangloporter`
- `mekanism:qio_dashboard`
- `mekanism:qio_drive_array`
- `mekanism:qio_drive_base`
- `mekanism:qio_drive_hyper_dense`
- `mekanism:qio_drive_time_dilating`
- `mekanism:qio_drive_supermassive`
- `mekanism:qio_importer`
- `mekanism:qio_exporter`
- `mekanism:qio_redstone_adapter`
- `mekanism:portable_qio_dashboard`
- `mekanism:robit`

理由は次のとおり。

- Digital Minerは地域資源と採掘拠点を無視する。
- Cardboard Boxはブロック移設制限や保護処理を迂回する可能性が高い。
- TeleporterはTPA、鉄道、車両、輸送路、Bastion侵入制限を無意味にする。
- QIOは国家領土の収納制限、略奪、物資輸送を無意味にする。
- Quantum Entangloporterは物資、流体、化学物質、エネルギー輸送を無線化し、補給線を消す。
- Robitは携帯収納・作業設備として収納制限と競合する。

### 5.3 戦闘・装備バランスを破壊するもの

以下を削除する。

- `mekanism:atomic_disassembler`
- `mekanism:meka_tool`
- `mekanism:mekasuit_helmet`
- `mekanism:mekasuit_bodyarmor`
- `mekanism:mekasuit_pants`
- `mekanism:mekasuit_boots`
- `mekanism:jetpack`
- `mekanism:jetpack_armored`
- `mekanism:module_jetpack_unit`
- `mekanism:module_teleportation_unit`
- Flamethrower
- Free Runners系装備
- MekaSuit用の戦闘・移動モジュール

これらは補強、工兵、TaCZ歩兵戦、Cold Sweat、ガスマスク、車両輸送の価値を下げる。

一方、次は原子力運用に必要なので残す。

- Hazmat Suit
- Geiger Counter
- Dosimeter
- Radiation Shielding関連
- Nuclear Waste Barrel

### 5.4 Createの加工・物流を置き換える機械

以下は削除候補ではなく、第一案では削除対象とする。

- `mekanism:energized_smelter`
- `mekanism:crusher`
- `mekanism:combiner`
- `mekanism:precision_sawmill`
- `mekanism:formulaic_assemblicator`
- 全Factoryレシピ
- Factory Installer系レシピ
- 全Logistical Transporter
- Logistical Sorter
- `mekanism:fuelwood_heater`

加工はCreateの粉砕、洗浄、プレス、ミキサー、かまどを使う。アイテム輸送はCreateのベルト、シュート、ファン、Mechanical Armを使う。

Mekanism機械の高速化をFactoryの1ブロックへ集約するのではなく、Createのベルト上へ複数のMekanism機械を並べることで工場を拡張させる。

Fuelwood Heaterは燃料だけで熱を作れてしまうため削除し、Thermal Evaporation Plantなどの加熱には電力式のResistive Heaterを使用する。

## 6. 鉱石倍化の削除

Mekanism標準の鉱石処理は最大5倍化まで存在する。

```text
Enrichment Chamber                     ×2
Purification Chamber                   ×3
Chemical Injection Chamber             ×4
Dissolution → Washer → Crystallizer    ×5
```

これはMoveEarthの地域資源量、採掘拠点、輸送量、国家間格差を大きく変える。このため、一般鉱石を増殖する処理レシピだけを選択的に削除する。

削除対象:

- 鉄、銅、金、錫、鉛、オスミウム等のore/raw oreからdustを増やすEnriching
- 一般鉱石をclumpへ変換するPurifying
- 一般鉱石をshardへ変換するInjecting
- clumpからdirty dustへのCrushing
- 一般鉱石のslurryを生成するDissolution
- ore slurry用のWashing
- ore slurry用のCrystallizing
- 上記中間素材を利用した2～5倍化経路

維持対象:

- Enriched Carbon、Enriched Redstone、Enriched Diamond等の注入素材生成
- UraniumからYellow Cake Uraniumへの核燃料処理
- Hydrofluoric Acid生成
- Uranium Oxide生成
- Uranium Hexafluoride生成
- Fissile Fuel生成
- Nuclear WasteからPolonium/Plutoniumへの処理

重要なのは、Enrichment ChamberやChemical Dissolution Chamber自体を削除しないことである。これらは核分裂燃料製造にも必要となる。

## 7. 残すMekanism設備

### 基礎・合金

- Metallurgic Infuser
- Osmium Compressor
- Enrichment Chamber
- 制御回路一式
- 合金一式
- Steel Casing

### 電気・熱・流体

- Electric Pump
- Electrolytic Separator
- Resistive Heater
- Fluid Tank
- Chemical Tank
- Dynamic Tank
- Mechanical Pipe
- Pressurized Tube
- Thermodynamic Conductor
- Configurator

Universal Cableは残し、Energy Cubeは全Tier削除する。

CEEのFE Converterから複数のMekanism機械、多ブロック設備、Induction Matrixへ電力を分配するには、施設内のFE配線が必要となる。この用途をUniversal Cableが担当する。

ただしUniversal CableがCEEの長距離送電網を置き換えないよう、次の設計にする。

- CEE: 発電所から都市・工場・原子力施設までの長距離送電、変圧、保護、計測を担当する。
- FE Converter: CEE系統とMekanism構内系統の受電点・送電点になる。
- Universal Cable: FE Converterより内側の工場・原子力施設内だけで使用する想定とする。
- Universal Cableの置換レシピにはCEEの絶縁材・配線部品とMekanism合金を要求する。
- 1回のクラフト出力数を抑え、ブロック単位で延ばす長距離配線はCEE架空線より明確に高コストにする。
- Basic/Advanced CableはMekanism基礎段階、Elite Cableは化学工業段階で解禁する。
- Ultimate Cableは核分裂到達後の部品を要求し、原子力施設向けとする。

Energy Cubeを残すと、CEEの蓄電器と電力設備を使わずMekanismだけで局所電力網を構築できるため削除する。通常の蓄電はCEEを使用し、核施設の急停止、出力変動、タービン復水停止を防ぐ大容量バッファについてのみ、原子力到達後のInduction Matrixを許可する。

### 化学・核燃料

- Pressurized Reaction Chamber
- Chemical Oxidizer
- Chemical Infuser
- Rotary Condensentrator
- Chemical Dissolution Chamber
- Isotopic Centrifuge
- Thermal Evaporation Plant
- Solar Neutron Activator

Solar Neutron Activatorは発電設備ではなく、核廃棄物からPoloniumを作る処理設備なので残す。

### 原子力・大型設備

- Fission Reactor
- Thermoelectric Boiler
- Industrial Turbine
- Induction Matrix
- Fusion Reactor
- Laser
- Laser Amplifier
- Laser Focus Matrix

## 8. 核分裂燃料チェーン

核分裂燃料は単純なクラフトではなく、次の工業ラインを必要とする。

### 硫酸系統

```text
石炭など
  ↓ Pressurized Reaction Chamber
Sulfur Dust
  ↓ Chemical Oxidizer
Sulfur Dioxide
  ↓ Chemical Infuser + Oxygen
Sulfur Trioxide
  ↓ Chemical Infuser + Water Vapor
Sulfuric Acid
```

### フッ酸系統

```text
Fluorite + Sulfuric Acid
  ↓ Chemical Dissolution Chamber
Hydrofluoric Acid
```

### ウラン系統

```text
Uranium
  ↓ Enrichment Chamber
Yellow Cake Uranium
  ↓ Chemical Oxidizer
Uranium Oxide
```

### 最終燃料

```text
Hydrofluoric Acid + Uranium Oxide
  ↓ Chemical Infuser
Uranium Hexafluoride
  ↓ Isotopic Centrifuge
Fissile Fuel
```

この工程は複数の化学物質、液体、気体、電力を扱うため、Create工場から原子力工場へ発展した実感を出しやすい。

## 9. レシピ改変方針

最初の冶金注入機だけを重くしても、一度作成した後はMekanismだけで自己完結する可能性がある。このため、複数段階へCreateとElectro Energeticsの部品を混ぜる。

### Metallurgic Infuser

Mekanismへの入口として、次の系統を要求する。

- Create Precision Mechanism
- Create Electron Tube
- Brass Casing
- Create: Electro Energeticsの電気部品
- FE変換設備またはそれを構成する部品

通常クラフトではなくMechanical Craftingにする案も有効。

第二段階では次の3×3 Mechanical Craftingとして実装する。

- CEE Converter ×1
- CEE Insulated Wire ×2
- Create Precision Mechanism ×2
- Create Brass Casing ×2
- Create Electron Tube ×1
- Osmium Ingot ×1

これにより、初回の冶金注入機はMekanism自身の鋼鉄や回路を要求せず自己循環を避けつつ、Create精密加工とCEE→FE変換設備の完成後にだけ作成できる。

### Universal Cable

第二段階では構内配線という役割を明確にするため、標準の安価な8本レシピを次へ置換する。

- Basic: Steel Ingot ×2 + CEE Insulated Wire ×1 → 4本
- Advanced: Basic Cable ×2 + Infused Alloy ×1 + CEE Insulated Wire ×2 → 2本
- Elite: Advanced Cable ×2 + Reinforced Alloy ×1 + CEE Heavily Insulated Wire ×2 → 2本
- Ultimate: Elite Cable ×2 + Atomic Alloy ×1 + CEE Heavily Insulated Wire ×3 + Polonium Pellet ×1 → 2本

Ultimateだけは核分裂後に解禁される。長距離送電をUniversal Cableで安価に代替できないよう、出力本数を抑え、各TierでCEE絶縁材を継続要求する。

### Steel Casing

- Mekanism Steel
- Create Sturdy Sheet
- Brass CasingまたはAndesite Casing
- Electro Energeticsの絶縁・配線部品

第三段階では3×3 Mechanical Craftingとして、Steel Ingot ×2、CEE Insulated Wire ×1、Create Sturdy Sheet ×2、Brass Casing ×1、安価なGlass Block ×2、Osmium Ingot ×1を要求する。

Steel Casing自体をCreate精密加工とCEE配線へ接続することで、以後のMekanism機械が標準レシピだけで独立増殖することを防ぐ。

### 化学機械

- Mekanism Control Circuit
- Electro Energeticsの制御・配線部品
- Createの板材、ケーシング、電子管

第三段階で次の標準レシピをMoveEarthレシピへ置換する。

- Enrichment Chamber: Precision Mechanism、Electron Tube、CEE Insulated Wireを追加
- Osmium Compressor: Precision Mechanism、CEE Heavily Insulated Wireを追加
- Electrolytic Separator: Steel Casing、Electron Tube、CEE Capacitor/Insulated Wireを追加
- Chemical Oxidizer: Personal Storage要求を廃止し、Steel Casing、CEE Relay/Insulated Wireへ置換
- Chemical Infuser: Steel Casing、CEE Relay/Heavily Insulated Wireを追加
- Rotary Condensentrator: Steel Casing、Precision Mechanism、CEE Insulated Wireを追加
- Chemical Dissolution Chamber: Precision Mechanism、CEE Heavily Insulated Wireを追加
- Isotopic Centrifuge: Steel Casing、CEE Heavily Insulated Wireを追加
- Pressurized Reaction Chamber: Steel Casing、CEE Heavily Insulated Wireを追加

鉱石3倍化を主用途とするPurification Chamberは再設計対象にせず、一般鉱石倍化の禁止方針に合わせて通常作成不能とする。核燃料へ必要な機械だけを残す。

### Industrial Turbine

- Turbine Rotor: Create Shaft、鋼鉄
- Turbine Blade: Sturdy Sheetまたは高級板材
- Rotational Complex: Flywheel、Precision Mechanism
- Electromagnetic Coil: Electro Energeticsのコイル・変圧器部品

第三段階後半では、Turbine CasingにBrass Casing/Sturdy Sheet、RotorにCreate Shaft、BladeにSturdy Sheet、Rotational ComplexにFlywheel/Precision Mechanism、Electromagnetic CoilにCEE Transformer Core/Heavily Insulated Wireを要求する。Saturating CondenserもCEE Radiator Panelへ接続する。

### Fission Reactor

- Reactor Casing: Lead、Atomic Alloy、Sturdy Sheet
- Reactor Port: Control Circuit、配管部品
- Control Rod: Lead、高Tier回路
- Fuel Assembly: 鋼鉄、鉛、化学耐性部品

第三段階後半では、Reactor CasingにAtomic Alloy/Sturdy Sheet、Control RodにPrecision Mechanism/CEE Redstone Relayを要求する。Fuel Assemblyは鉛と鋼鉄を大量消費する構成を維持し、炉の大型化が継続的な重金属需要へ直結するようにする。

### Boiler・Induction Matrix

- Boiler Casing、Pressure Disperser、Superheating ElementをCreate Sturdy SheetおよびCEE高耐圧部品へ接続する。
- Energy Cubeは削除済みのため、Induction Cell/Provider全TierからEnergy Cube要求を除去する。
- Basic Induction Cell/ProviderはPolonium Pelletを要求し、Matrix全体を核分裂後の安全設備として解禁する。
- 上位Cell/Providerは前Tier、CEE High Voltage Capacitor、高Tier回路・合金を大量に要求する。
- Induction CasingもCEE High Voltage Capacitor、Heavily Insulated Wire、Create Sturdy Sheetを要求する。

これにより、Induction Matrixが作成不能になる循環を解消しながら、原子炉以前の汎用蓄電設備として先行利用されることを防ぐ。

この構成なら、原子炉本体だけでなくタービンにもCreateと電力技術の蓄積が必要になる。

## 10. 資源生成方針

Mekanismは次の独自資源を追加する。

- Osmium
- Tin
- Lead
- Uranium
- Fluorite

既存Modと重複するTin、Leadは共通タグへ統合し、ワールド生成を二重化しない。

UraniumとFluoriteは原子力の戦略資源として、MoveEarthの地域資源システムへ統合する。

推奨:

- Mekanism標準のUranium/Fluorite生成を無効化
- 地域ごとの鉱床・鉱脈として生成
- Uranium地域とFluorite地域を完全には一致させない
- 原子力国家が採掘拠点、鉄道、輸送車両、同盟または貿易を必要とする配置にする
- 核燃料をQIOやEntangloporterで無線輸送できないようにする

これにより原子力が単なるクラフトツリーではなく、領土・外交・物流の目的になる。

## 11. 核融合・反物質の扱い

第一実装は核分裂炉と産業用タービンまでに絞る。

核融合炉は出力規模が極端に大きいため、核分裂の実測データを取った後に解禁する。

初期段階では次を無効化する。

- `mekanism:antiprotonic_nucleosynthesizer`
- SPS関連レシピ
- Antimatterを利用する物質生成レシピ

Antiprotonic Nucleosynthesizerはエネルギーから資源を生成できるため、地域資源経済を再び破壊する。

将来SPSを復活させる場合は、Antimatterを通常資源生成ではなく、国家規模の特殊設備、領土アップグレード、超大型兵器部品などMoveEarth独自用途へ限定する。

## 12. 電力接続上の確認事項

Create: Electro Energeticsは独自の電圧・電流シミュレーションを使用し、FE変換用の設備を持つ。

CEE公式1.21.1ブランチの実装では、`electroenergetics:converter`は双方向変換に対応している。

- 受電モード: CEE側の電力を内部へ取り込み、隣接するFE機械・ケーブルへ供給する。
- 送電モード: 隣接するFE設備から電力を取り出し、設定電圧でCEE側へ供給する。
- 既定変換率: 34 W = 1 FE/t
- 既定Converter上限: 100 kW
- 既定値では単一Converterあたり約2,941 FE/t相当となる。

したがって、通常のMekanism工場は単一または少数Converter、原子力発電所は複数Converterを並列配置した変電所を必要とする構成が自然である。

採用する実バイナリについて次を実機確認する。

- Electro Energetics電力からFEへ変換できるか
- FEからElectro Energetics電力へ逆変換できるか
- 双方向変換時の効率
- 電圧不足、過電流、短絡時の挙動
- チャンクアンロード時のMekanism機械との同期
- 核分裂タービンのFEをCEE電力網へ戻せるか
- Converterを並列配置した時に出力が正しく合算されるか
- `converterMaxPower`と`wattFeTConversionRate`の設定変更が既存設備へ安全に反映されるか

採用バイナリで双方向変換が利用できない場合に限り、原子力発電をCEE電力網へ戻すMoveEarth専用系統連系ブリッジを検討する。通常方針ではCEE標準Converterを使用する。

## 13. 実装方法

既存のTaCZ作業台およびPortable Engineと同様に、サーバー起動時・データパック再読込時にレシピを監査して差し替える方式を利用できる。

推奨構成:

- `MekanismRecipePolicy`
  - 削除対象ID
  - 残す核燃料レシピ
  - 置換レシピID
  - 一般鉱石処理の判定
- `MekanismRecipeHandler`
  - Mekanismが導入されている時だけ動作
  - 置換レシピがロードできた時だけ旧レシピを削除
  - 削除数とカテゴリをログ出力
- MoveEarth名前空間の置換レシピ
- JEI上の確認
- サーバー設定による統合機能の有効・無効化

誤って核燃料レシピまで削除しないため、鉱石処理は大きなパス単位で一括削除せず、入力タグと出力種類を含めて判定する。

## 14. テスト項目

### 依存・ロード

- MekanismなしでMoveEarthが起動するか、または必須依存として明確に停止するか
- Mekanism本体のみでなくGeneratorsも必須として検出されるか
- プレイヤー用・サーバー用jarでクラスロード競合がないか

### レシピ

- 独立発電機が作成不能
- Digital Miner/QIO/Teleportが作成不能
- 強力装備が作成不能
- 一般鉱石の2～5倍化が不可能
- 核燃料用のEnrichment/Dissolution/Infusingは利用可能
- 置換レシピ欠落時に旧レシピを誤削除しない
- データパック再読込後も同じ状態を維持

### 電力

- Create蒸気からElectro Energetics発電へ進める
- FE Converter経由で最初のMetallurgic Infuserを動かせる
- Mekanism独自発電なしでは自己始動できない
- 核分裂タービンの出力を既存電力網で利用できる

### 原子力

- UraniumとFluoriteからFissile Fuelを完成できる
- Nuclear Wasteが適切に蓄積される
- 冷却不足、廃棄物詰まり、出力停止時の挙動
- 放射線とメルトダウンが領土・補強・Siegeへ与える影響
- サーバー再起動後も多ブロック設備と化学物質が保持される

### ゲームバランス

- Createの加工設備がMekanism導入後も必要
- Electro Energeticsの送電網がMekanismケーブルに置換されない
- 地域資源と輸送路が原子力進行に必要
- 小型発電の大量設置が核分裂を上回らない
- 原子力が国家規模の投資として成立する

## 15. 推奨実装順

1. Mekanism本体、Generators、CEEの正確な採用バージョンを固定する。
2. CEE FE Converterの両方向変換と変換効率を実機で確認する。
3. 必須依存宣言とcompileOnly依存を追加する。
4. レシピIDとレジストリIDの監査テストを作る。
5. 独立発電、Energy Cube、Digital Miner、QIO、Teleport、強力装備を無効化する。
6. Universal CableをCEE構内配線として再レシピ化し、Tierごとの解禁段階を設定する。
7. 一般鉱石倍化だけを選択的に無効化する。
8. Metallurgic InfuserへのCreate・CEE入口を作る。
9. Steel Casingと化学機械のレシピを再設計する。
10. UraniumとFluoriteを地域資源へ統合する。
11. 核分裂燃料チェーンを実機で通す。
12. Fission Reactor、Boiler、Industrial Turbine、Induction Matrixを再設計する。
13. 原子力発電をCEE電力網へ戻せることを確認する。
14. 実戦サーバーで核分裂までの所要時間、資源量、出力を測る。
15. データ取得後に核融合とSPSの採否を決める。

## 16. 現時点の推奨結論

Mekanismの採用自体は推奨できる。

ただし、採用する中心は次の範囲とする。

- 合金と制御回路
- 電気化学設備
- 核燃料製造
- 放射線と核廃棄物
- 核分裂炉
- 産業用タービン
- 将来的な核融合炉

Mekanismの便利機能、自己完結型発電、鉱石倍化、無線物流、遠隔収納、Teleport、高性能装備はMoveEarthの既存設計と競合するため、積極的に削る。

「Mekanismを追加する」のではなく、**Mekanismの原子力・化学部分をCreate文明の終盤技術として再構成する**のが本計画の基本方針となる。
