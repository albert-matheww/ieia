-- =============================================================================
-- IEIA — V2: Reference data
--   * activity_types   : catalogue of loggable activities
--   * emission_factors : configurable conversion factors (ILLUSTRATIVE DEFAULTS —
--                        an ADMIN should verify/replace against an authoritative,
--                        region-correct dataset before production use)
--   * scoring_configs  : the active scoring methodology (v1)
--   * benchmarks       : clearly-labelled configurable comparison values
--
-- Sources referenced below are the *kind* of dataset each figure is drawn from
-- (UK DEFRA 2024 GHG conversion factors, India CEA CO2 Baseline Database,
-- IPCC AR6 WG3, ecoinvent-derived literature values). Values are rounded and
-- provided so the platform is demonstrable out of the box.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- ACTIVITY TYPES
-- -----------------------------------------------------------------------------
INSERT INTO activity_types (code, category, name, description, default_unit, allowed_units, attribute_schema) VALUES
('electricity_grid',        'ELECTRICITY', 'Grid electricity',            'Electricity drawn from the public grid',              'kWh',   '["kWh","MWh"]',            '{"source_detail":{"label":"Supply","options":["grid","mixed"]}}'),
('electricity_renewable',   'ELECTRICITY', 'Renewable electricity',       'Rooftop solar / green tariff / on-site renewable',    'kWh',   '["kWh","MWh"]',            '{"source_detail":{"label":"Source","options":["rooftop_solar","green_tariff","wind"]}}'),
('petrol',                  'FUEL',        'Petrol (motor gasoline)',     'Petrol combusted (generator, equipment, stove)',     'litre', '["litre"]',                '{"usage_category":{"label":"Use","options":["generator","equipment","other"]}}'),
('diesel',                  'FUEL',        'Diesel',                      'Diesel combusted (DG set, equipment)',               'litre', '["litre"]',                '{"usage_category":{"label":"Use","options":["dg_set","equipment","other"]}}'),
('lpg',                     'FUEL',        'LPG',                         'Liquefied petroleum gas (cooking / heating)',        'kg',    '["kg"]',                   '{"usage_category":{"label":"Use","options":["cooking","heating","other"]}}'),
('natural_gas',             'FUEL',        'Natural gas (PNG)',           'Piped natural gas',                                   'm3',    '["m3"]',                   '{"usage_category":{"label":"Use","options":["cooking","heating","process"]}}'),
('cng',                     'FUEL',        'CNG',                         'Compressed natural gas (vehicle fuel)',              'kg',    '["kg"]',                   '{}'),
('car_petrol',              'TRANSPORT',   'Car - petrol',               'Passenger car, petrol',                               'km',    '["km"]',                   '{"fuel_type":{"fixed":"petrol"},"num_trips":{"label":"Trips"},"fuel_consumed_litre":{"label":"Fuel used (L, optional)"}}'),
('car_diesel',              'TRANSPORT',   'Car - diesel',               'Passenger car, diesel',                               'km',    '["km"]',                   '{"fuel_type":{"fixed":"diesel"},"num_trips":{"label":"Trips"},"fuel_consumed_litre":{"label":"Fuel used (L, optional)"}}'),
('two_wheeler',             'TRANSPORT',   'Two-wheeler',                'Motorcycle / scooter, petrol',                       'km',    '["km"]',                   '{"num_trips":{"label":"Trips"}}'),
('auto_rickshaw',           'TRANSPORT',   'Auto rickshaw',              'Three-wheeler (CNG/petrol)',                          'km',    '["km"]',                   '{"num_trips":{"label":"Trips"}}'),
('bus',                     'TRANSPORT',   'Bus',                        'Public bus (passenger-km)',                           'km',    '["km"]',                   '{"num_trips":{"label":"Trips"}}'),
('metro_rail',              'TRANSPORT',   'Metro / suburban rail',      'Electric metro or suburban train (passenger-km)',    'km',    '["km"]',                   '{"num_trips":{"label":"Trips"}}'),
('domestic_flight',         'TRANSPORT',   'Domestic flight',            'Short-haul flight (passenger-km)',                    'km',    '["km"]',                   '{"num_trips":{"label":"Flights"}}'),
('municipal_water',         'WATER',       'Municipal water',            'Treated water from utility supply',                   'litre', '["litre","m3","kL"]',      '{"usage_category":{"label":"Usage","options":["domestic","irrigation","process","cooling"]}}'),
('groundwater_pumped',      'WATER',       'Pumped groundwater',        'Borewell / well water (electric pump)',              'litre', '["litre","m3","kL"]',      '{"usage_category":{"label":"Usage","options":["domestic","irrigation","process"]}}'),
('waste_mixed',             'WASTE',       'Mixed municipal waste',     'General waste to landfill',                           'kg',    '["kg","tonne"]',           '{"disposal_method":{"fixed":"landfill"}}'),
('waste_recycled',          'WASTE',       'Recycled waste',            'Segregated dry waste sent for recycling',            'kg',    '["kg","tonne"]',           '{"disposal_method":{"fixed":"recycling"}}'),
('waste_organic_compost',   'WASTE',       'Organic waste - composted', 'Wet/organic waste composted',                         'kg',    '["kg","tonne"]',           '{"disposal_method":{"fixed":"composting"}}'),
('waste_incinerated',       'WASTE',       'Incinerated waste',         'Waste to incineration / WtE',                         'kg',    '["kg","tonne"]',           '{"disposal_method":{"fixed":"incineration"}}'),
('material_steel',          'MATERIALS',   'Steel',                     'Virgin steel procured/used',                         'kg',    '["kg","tonne"]',           '{"usage_category":{"label":"Use","options":["construction","equipment","packaging"]}}'),
('material_cement',         'MATERIALS',   'Cement',                    'Portland cement procured/used',                      'kg',    '["kg","tonne"]',           '{}'),
('material_aluminium',      'MATERIALS',   'Aluminium',                 'Virgin aluminium procured/used',                     'kg',    '["kg","tonne"]',           '{}'),
('material_paper',          'MATERIALS',   'Paper & board',             'Paper / cardboard procured/used',                    'kg',    '["kg","tonne"]',           '{}'),
('material_plastic',        'MATERIALS',   'Plastic',                   'Virgin plastic procured/used',                       'kg',    '["kg","tonne"]',           '{}');

-- -----------------------------------------------------------------------------
-- EMISSION FACTORS
--   impact = amount(unit_from) * value  ->  unit_to
--   Regions: 'GLOBAL' default; 'IN' overrides where India-specific.
-- -----------------------------------------------------------------------------

-- Electricity: CO2e + energy content (1 kWh = 3.6 MJ)
INSERT INTO emission_factors (activity_type_id, impact_type, unit_from, unit_to, value, region, source, effective_from) VALUES
((SELECT id FROM activity_types WHERE code='electricity_grid'),      'CO2E',   'kWh', 'kgCO2e', 0.716000, 'IN',     'India CEA CO2 Baseline Database (grid avg ~0.71 kgCO2/kWh)', DATE '2023-01-01'),
((SELECT id FROM activity_types WHERE code='electricity_grid'),      'CO2E',   'kWh', 'kgCO2e', 0.475000, 'GLOBAL', 'IEA world average grid intensity (approx.)',                 DATE '2023-01-01'),
((SELECT id FROM activity_types WHERE code='electricity_grid'),      'ENERGY', 'kWh', 'MJ',     3.600000, 'GLOBAL', 'Unit conversion 1 kWh = 3.6 MJ',                             DATE '2000-01-01'),
((SELECT id FROM activity_types WHERE code='electricity_renewable'), 'CO2E',   'kWh', 'kgCO2e', 0.041000, 'GLOBAL', 'IPCC AR5/AR6 lifecycle median, solar PV',                    DATE '2020-01-01'),
((SELECT id FROM activity_types WHERE code='electricity_renewable'), 'ENERGY', 'kWh', 'MJ',     3.600000, 'GLOBAL', 'Unit conversion 1 kWh = 3.6 MJ',                             DATE '2000-01-01');

-- Fuels: CO2e + energy content (net calorific value)
INSERT INTO emission_factors (activity_type_id, impact_type, unit_from, unit_to, value, region, source, effective_from) VALUES
((SELECT id FROM activity_types WHERE code='petrol'),      'CO2E',   'litre', 'kgCO2e', 2.310000, 'GLOBAL', 'UK DEFRA 2024 GHG conversion factors (petrol)',   DATE '2024-01-01'),
((SELECT id FROM activity_types WHERE code='petrol'),      'ENERGY', 'litre', 'MJ',     34.200000,'GLOBAL', 'NCV motor gasoline ~34.2 MJ/L',                   DATE '2000-01-01'),
((SELECT id FROM activity_types WHERE code='diesel'),      'CO2E',   'litre', 'kgCO2e', 2.680000, 'GLOBAL', 'UK DEFRA 2024 GHG conversion factors (diesel)',   DATE '2024-01-01'),
((SELECT id FROM activity_types WHERE code='diesel'),      'ENERGY', 'litre', 'MJ',     38.600000,'GLOBAL', 'NCV diesel ~38.6 MJ/L',                           DATE '2000-01-01'),
((SELECT id FROM activity_types WHERE code='lpg'),         'CO2E',   'kg',    'kgCO2e', 2.983000, 'GLOBAL', 'UK DEFRA 2024 GHG conversion factors (LPG)',      DATE '2024-01-01'),
((SELECT id FROM activity_types WHERE code='lpg'),         'ENERGY', 'kg',    'MJ',     46.100000,'GLOBAL', 'NCV LPG ~46.1 MJ/kg',                             DATE '2000-01-01'),
((SELECT id FROM activity_types WHERE code='natural_gas'), 'CO2E',   'm3',    'kgCO2e', 2.020000, 'GLOBAL', 'UK DEFRA 2024 GHG conversion factors (natural gas, gross m3)', DATE '2024-01-01'),
((SELECT id FROM activity_types WHERE code='natural_gas'), 'ENERGY', 'm3',    'MJ',     38.700000,'GLOBAL', 'GCV natural gas ~38.7 MJ/m3',                     DATE '2000-01-01'),
((SELECT id FROM activity_types WHERE code='cng'),         'CO2E',   'kg',    'kgCO2e', 2.680000, 'GLOBAL', 'DEFRA 2024 (CNG, well-to-wheel approx.)',         DATE '2024-01-01'),
((SELECT id FROM activity_types WHERE code='cng'),         'ENERGY', 'kg',    'MJ',     47.100000,'GLOBAL', 'NCV natural gas ~47.1 MJ/kg',                     DATE '2000-01-01');

-- Transport: CO2e per km (tailpipe + upstream, per vehicle or per passenger-km)
INSERT INTO emission_factors (activity_type_id, impact_type, unit_from, unit_to, value, region, source, effective_from) VALUES
((SELECT id FROM activity_types WHERE code='car_petrol'),     'CO2E', 'km', 'kgCO2e', 0.170000, 'GLOBAL', 'DEFRA 2024 average petrol car (per vehicle-km)',        DATE '2024-01-01'),
((SELECT id FROM activity_types WHERE code='car_diesel'),     'CO2E', 'km', 'kgCO2e', 0.164000, 'GLOBAL', 'DEFRA 2024 average diesel car (per vehicle-km)',        DATE '2024-01-01'),
((SELECT id FROM activity_types WHERE code='two_wheeler'),    'CO2E', 'km', 'kgCO2e', 0.095000, 'GLOBAL', 'DEFRA 2024 average motorcycle (per vehicle-km)',        DATE '2024-01-01'),
((SELECT id FROM activity_types WHERE code='auto_rickshaw'),  'CO2E', 'km', 'kgCO2e', 0.110000, 'IN',     'Literature estimate, CNG three-wheeler (per vehicle-km)',DATE '2020-01-01'),
((SELECT id FROM activity_types WHERE code='bus'),            'CO2E', 'km', 'kgCO2e', 0.100000, 'GLOBAL', 'DEFRA 2024 local bus (per passenger-km)',               DATE '2024-01-01'),
((SELECT id FROM activity_types WHERE code='metro_rail'),     'CO2E', 'km', 'kgCO2e', 0.035000, 'GLOBAL', 'DEFRA 2024 national/metro rail (per passenger-km)',      DATE '2024-01-01'),
((SELECT id FROM activity_types WHERE code='domestic_flight'),'CO2E', 'km', 'kgCO2e', 0.246000, 'GLOBAL', 'DEFRA 2024 domestic flight incl. RF uplift (per pkm)',   DATE '2024-01-01');

-- Water: CO2e per litre (abstraction, treatment, distribution) + water footprint
INSERT INTO emission_factors (activity_type_id, impact_type, unit_from, unit_to, value, region, source, effective_from) VALUES
((SELECT id FROM activity_types WHERE code='municipal_water'),    'CO2E',  'litre', 'kgCO2e', 0.000344, 'GLOBAL', 'DEFRA 2024 water supply ~0.344 kgCO2e/m3',      DATE '2024-01-01'),
((SELECT id FROM activity_types WHERE code='municipal_water'),    'WATER', 'litre', 'litre',  1.000000, 'GLOBAL', 'Direct water withdrawal',                        DATE '2000-01-01'),
((SELECT id FROM activity_types WHERE code='groundwater_pumped'), 'CO2E',  'litre', 'kgCO2e', 0.000450, 'IN',     'Est. incl. electric pumping (grid IN)',          DATE '2023-01-01'),
((SELECT id FROM activity_types WHERE code='groundwater_pumped'), 'WATER', 'litre', 'litre',  1.000000, 'GLOBAL', 'Direct water withdrawal',                        DATE '2000-01-01');

-- Waste: CO2e per kg by disposal route + waste mass (landfilled counts toward waste impact)
INSERT INTO emission_factors (activity_type_id, impact_type, unit_from, unit_to, value, region, source, effective_from) VALUES
((SELECT id FROM activity_types WHERE code='waste_mixed'),           'CO2E',  'kg', 'kgCO2e', 0.450000, 'GLOBAL', 'DEFRA 2024 mixed municipal waste to landfill',  DATE '2024-01-01'),
((SELECT id FROM activity_types WHERE code='waste_mixed'),           'WASTE', 'kg', 'kg',     1.000000, 'GLOBAL', 'Direct mass, landfilled',                        DATE '2000-01-01'),
((SELECT id FROM activity_types WHERE code='waste_recycled'),        'CO2E',  'kg', 'kgCO2e', 0.021000, 'GLOBAL', 'DEFRA 2024 open-loop recycling (mixed)',        DATE '2024-01-01'),
((SELECT id FROM activity_types WHERE code='waste_recycled'),        'WASTE', 'kg', 'kg',     0.000000, 'GLOBAL', 'Diverted from landfill (0 landfilled mass)',    DATE '2000-01-01'),
((SELECT id FROM activity_types WHERE code='waste_organic_compost'), 'CO2E',  'kg', 'kgCO2e', 0.010000, 'GLOBAL', 'DEFRA 2024 composting',                          DATE '2024-01-01'),
((SELECT id FROM activity_types WHERE code='waste_organic_compost'), 'WASTE', 'kg', 'kg',     0.000000, 'GLOBAL', 'Diverted from landfill',                         DATE '2000-01-01'),
((SELECT id FROM activity_types WHERE code='waste_incinerated'),     'CO2E',  'kg', 'kgCO2e', 0.350000, 'GLOBAL', 'DEFRA 2024 incineration (municipal, no ER credit)', DATE '2024-01-01'),
((SELECT id FROM activity_types WHERE code='waste_incinerated'),     'WASTE', 'kg', 'kg',     0.200000, 'GLOBAL', 'Residual ash mass fraction (approx.)',           DATE '2000-01-01');

-- Materials: embodied CO2e per kg (cradle-to-gate, virgin)
INSERT INTO emission_factors (activity_type_id, impact_type, unit_from, unit_to, value, region, source, effective_from) VALUES
((SELECT id FROM activity_types WHERE code='material_steel'),     'CO2E', 'kg', 'kgCO2e', 1.850000, 'GLOBAL', 'worldsteel / ecoinvent-derived, virgin steel',   DATE '2020-01-01'),
((SELECT id FROM activity_types WHERE code='material_cement'),    'CO2E', 'kg', 'kgCO2e', 0.860000, 'GLOBAL', 'GCCA / literature, Portland cement',             DATE '2020-01-01'),
((SELECT id FROM activity_types WHERE code='material_aluminium'), 'CO2E', 'kg', 'kgCO2e', 8.240000, 'GLOBAL', 'IAI / ecoinvent-derived, virgin aluminium',      DATE '2020-01-01'),
((SELECT id FROM activity_types WHERE code='material_paper'),     'CO2E', 'kg', 'kgCO2e', 0.919000, 'GLOBAL', 'DEFRA 2024 paper (primary production)',          DATE '2024-01-01'),
((SELECT id FROM activity_types WHERE code='material_plastic'),   'CO2E', 'kg', 'kgCO2e', 1.900000, 'GLOBAL', 'DEFRA 2024 average plastics (primary)',          DATE '2024-01-01');

-- -----------------------------------------------------------------------------
-- SCORING CONFIG  (see docs/06-scoring-methodology.md)
--   baselines.<scope>.<region>.<METRIC> = { "good": g, "poor": p, "unit": "..." }
--   sub_score(m) = clamp( 100 * (p - m) / (p - g), 0, 100 )
-- -----------------------------------------------------------------------------
INSERT INTO scoring_configs (version, is_active, weights, baselines, scale_params, description) VALUES
('v1', TRUE,
 '{"carbon":0.35,"energy":0.20,"water":0.15,"waste":0.15,"transport":0.15}'::jsonb,
 '{
    "INDIVIDUAL": {
      "GLOBAL": {
        "CO2E":      {"good": 250,  "poor": 1200, "unit": "kgCO2e/month"},
        "ENERGY":    {"good": 1800, "poor": 9000, "unit": "MJ/month"},
        "WATER":     {"good": 3000, "poor": 12000,"unit": "litre/month"},
        "WASTE":     {"good": 15,   "poor": 90,   "unit": "kg landfilled/month"},
        "TRANSPORT": {"good": 60,   "poor": 400,  "unit": "kgCO2e/month"}
      }
    },
    "ORGANIZATION": {
      "GLOBAL": {
        "CO2E":      {"good": 400,  "poor": 2000,  "unit": "kgCO2e/employee/month"},
        "ENERGY":    {"good": 3000, "poor": 15000, "unit": "MJ/employee/month"},
        "WATER":     {"good": 2000, "poor": 10000, "unit": "litre/employee/month"},
        "WASTE":     {"good": 20,   "poor": 120,   "unit": "kg landfilled/employee/month"},
        "TRANSPORT": {"good": 100,  "poor": 600,   "unit": "kgCO2e/employee/month"}
      }
    }
 }'::jsonb,
 '{"clamp":[0,100],"recycling_bonus_max":8,"round_dp":2,"insufficient_data_months":2}'::jsonb,
 'IEIA baseline scoring methodology v1. Linear normalisation of each metric against configurable good/poor thresholds; weighted aggregate. Fully documented in docs/06-scoring-methodology.md.');

-- -----------------------------------------------------------------------------
-- BENCHMARKS  (CONFIGURABLE — not official standards; is_official = FALSE)
-- -----------------------------------------------------------------------------
INSERT INTO benchmarks (scope, segment, metric, unit, period, value, rating_bands, region, source, is_official, effective_from) VALUES
('INDIVIDUAL','GLOBAL_AVG',         'CO2E','kgCO2e','MONTH',  400,  '{"good":[0,250],"average":[250,600],"needs_improvement":[600,null]}'::jsonb, 'GLOBAL','Derived from ~4.7 tCO2/capita/yr world average', FALSE, DATE '2023-01-01'),
('INDIVIDUAL','IN_URBAN_HOUSEHOLD', 'CO2E','kgCO2e','MONTH',  180,  '{"good":[0,120],"average":[120,260],"needs_improvement":[260,null]}'::jsonb, 'IN','Derived from ~2 tCO2/capita/yr India average (illustrative)', FALSE, DATE '2023-01-01'),
('INDIVIDUAL','IN_URBAN_HOUSEHOLD', 'ENERGY','MJ','MONTH',    2400, '{"good":[0,1800],"average":[1800,3600],"needs_improvement":[3600,null]}'::jsonb,'IN','Illustrative household energy use', FALSE, DATE '2023-01-01'),
('INDIVIDUAL','IN_URBAN_HOUSEHOLD', 'WATER','litre','MONTH',  4500, '{"good":[0,3000],"average":[3000,6000],"needs_improvement":[6000,null]}'::jsonb,'IN','~150 L/person/day CPHEEO norm (illustrative)', FALSE, DATE '2023-01-01'),
('INDIVIDUAL','IN_URBAN_HOUSEHOLD', 'WASTE','kg','MONTH',     30,   '{"good":[0,15],"average":[15,45],"needs_improvement":[45,null]}'::jsonb,     'IN','~0.5 kg/person/day urban India (illustrative)', FALSE, DATE '2023-01-01'),
('INDIVIDUAL','GLOBAL_AVG',         'SCORE','points','MONTH', 60,   '{"needs_improvement":[0,50],"average":[50,70],"good":[70,101]}'::jsonb,       'GLOBAL','IEIA reference score midpoint', FALSE, DATE '2023-01-01'),
('INDIVIDUAL','IN_URBAN_HOUSEHOLD', 'SCORE','points','MONTH', 60,   '{"needs_improvement":[0,50],"average":[50,70],"good":[70,101]}'::jsonb,       'IN','IEIA reference score midpoint', FALSE, DATE '2023-01-01'),
('ORGANIZATION','OFFICE_PER_EMPLOYEE','SCORE','points','MONTH', 60, '{"needs_improvement":[0,50],"average":[50,70],"good":[70,101]}'::jsonb,       'GLOBAL','IEIA reference score midpoint', FALSE, DATE '2023-01-01'),
('ORGANIZATION','OFFICE_PER_EMPLOYEE','CO2E','kgCO2e','MONTH', 550, '{"good":[0,400],"average":[400,800],"needs_improvement":[800,null]}'::jsonb,  'GLOBAL','Illustrative office-based per-employee footprint', FALSE, DATE '2023-01-01'),
('ORGANIZATION','OFFICE_PER_EMPLOYEE','ENERGY','MJ','MONTH',   4200,'{"good":[0,3000],"average":[3000,6000],"needs_improvement":[6000,null]}'::jsonb,'GLOBAL','Illustrative office energy intensity', FALSE, DATE '2023-01-01'),
('ORGANIZATION','OFFICE_PER_EMPLOYEE','WATER','litre','MONTH', 3000,'{"good":[0,2000],"average":[2000,5000],"needs_improvement":[5000,null]}'::jsonb,'GLOBAL','Illustrative office water intensity', FALSE, DATE '2023-01-01'),
('ORGANIZATION','OFFICE_PER_EMPLOYEE','WASTE','kg','MONTH',    25,  '{"good":[0,20],"average":[20,60],"needs_improvement":[60,null]}'::jsonb,       'GLOBAL','Illustrative office waste intensity', FALSE, DATE '2023-01-01');

-- =============================================================================
-- End of V2
-- =============================================================================
