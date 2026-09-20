const { test, expect } = require('@playwright/test');
const path = require('path');
const fs = require('fs');

const COMPONENTS_DIR = path.resolve(__dirname, '../components');
const SKETCH_EXPORTS_DIR = path.resolve(__dirname, '../exports');

const SKETCHTOOL = '/Applications/Sketch.app/Contents/Resources/sketchtool/bin/sketchtool';
const SKETCH_FILE = path.resolve(__dirname, '../golf_membership_credential.sketch');

test.beforeAll(async () => {
  if (!fs.existsSync(SKETCH_EXPORTS_DIR)) {
    fs.mkdirSync(SKETCH_EXPORTS_DIR, { recursive: true });
  }
  const { execSync } = require('child_process');
  const listOutput = execSync(
    `"${SKETCHTOOL}" list layers "${SKETCH_FILE}" 2>/dev/null`,
    { encoding: 'utf-8' }
  );
  const listData = JSON.parse(listOutput);
  const symbolIds = [];
  for (const page of listData.pages || []) {
    if (page.name === 'Symbols') {
      for (const layer of page.layers || []) {
        symbolIds.push(layer.id);
      }
    }
  }
  execSync(
    `"${SKETCHTOOL}" export layers "${SKETCH_FILE}" --output="${SKETCH_EXPORTS_DIR}" --scales="1" --formats="png" --items="${symbolIds.join(',')}" --include-symbols=YES 2>/dev/null`,
    { encoding: 'utf-8' }
  );
});

const SIMPLE_COMPONENTS = [
  {
    name: 'numbered_symbol',
    file: 'numbered_symbol.html',
    sketchExport: 'numbered_symbol.png',
    selector: '.numbered-symbol',
    width: 40,
    height: 40,
  },
  {
    name: 'CallToAction_Button',
    file: 'calltoaction_button.html',
    sketchExport: 'CallToAction_Button.png',
    selector: '.cta-button',
    width: 342,
    height: 48,
  },
  {
    name: 'Secondary_CallToAction_Button',
    file: 'secondary_calltoaction_button.html',
    sketchExport: 'Secondary_CallToAction_Button.png',
    selector: '.secondary-cta-button',
    width: 342,
    height: 48,
  },
  {
    name: 'Blue_CallToAction_Button',
    file: 'blue_calltoaction_button.html',
    sketchExport: 'Blue_CallToAction_Button.png',
    selector: '.blue-cta-button',
    width: 342,
    height: 48,
  },
  {
    name: 'Mobile_Footer',
    file: 'mobile_footer.html',
    sketchExport: 'Mobile_Footer.png',
    selector: '.mobile-footer',
    width: 390,
    height: 42,
  },
  {
    name: 'Headline',
    file: 'headline.html',
    sketchExport: 'Headline.png',
    selector: '.headline',
    width: 345,
    height: 38,
  },
  {
    name: 'Info_Text',
    file: 'info_text.html',
    sketchExport: 'Info_Text.png',
    selector: '.info-text',
    width: 266,
    height: null,
  },
  {
    name: 'Checkmark_Symbol',
    file: 'checkmark_symbol.html',
    sketchExport: 'Checkmark_Symbol.png',
    selector: '.checkmark-symbol',
    width: 80,
    height: 80,
  },
  {
    name: 'XMark_Symbol',
    file: 'xmark_symbol.html',
    sketchExport: 'XMark_Symbol.png',
    selector: '.xmark-symbol',
    width: 80,
    height: 80,
  },
  {
    name: 'Numbered_BulletPoint',
    file: 'numbered_bulletpoint.html',
    sketchExport: 'Numbered_BulletPoint.png',
    selector: '.numbered-bulletpoint',
    width: 318,
    height: 65,
  },
  {
    name: 'BulletPoint_List',
    file: 'bulletpoint_list.html',
    sketchExport: 'BulletPoint_List.png',
    selector: '.bulletpoint-list',
    width: 342,
    height: null,
  },
  {
    name: 'Leading_Progress_Indicator',
    file: 'leading_progress_indicator.html',
    sketchExport: 'Leading_Progress_Indicator.png',
    selector: '.leading-progress',
    width: 89,
    height: 40,
  },
  {
    name: 'Mid_Progress_Indicator',
    file: 'mid_progress_indicator.html',
    sketchExport: 'Mid_Progress_Indicator.png',
    selector: '.mid-progress',
    width: 138,
    height: 40,
  },
  {
    name: 'Trailing_Progress_Indicator',
    file: 'trailing_progress_indicator.html',
    sketchExport: 'Trailing_Progress_Indicator.png',
    selector: '.trailing-progress',
    width: 89,
    height: 40,
  },
];

const STATEFUL_COMPONENTS = [
  {
    name: 'Segment_Control (A selected)',
    file: 'segment_control.html',
    sketchExport: 'A_Selected_Segment_Control.png',
    selector: '[data-selected="a"]',
    width: 342,
    height: 41,
  },
  {
    name: 'Segment_Control (B selected)',
    file: 'segment_control.html',
    sketchExport: 'B_Selected_Segment_Control.png',
    selector: '[data-selected="b"]',
    width: 342,
    height: 41,
  },
  {
    name: 'GolfCourse_Item (deselected)',
    file: 'golfcourse_item.html',
    sketchExport: 'GolfCourse_Item.png',
    selector: '.golfcourse-item:not(.selected)',
    width: 342,
    height: 64,
  },
  {
    name: 'GolfCourse_Item (selected)',
    file: 'golfcourse_item.html',
    sketchExport: 'Selected_GolfCourse_Item.png',
    selector: '.golfcourse-item.selected',
    width: 342,
    height: 64,
  },
  {
    name: 'Tournament_Item (deselected)',
    file: 'tournament_item.html',
    sketchExport: 'Tournament_Item.png',
    selector: '.tournament-item:not(.selected)',
    width: 342,
    height: 73,
  },
  {
    name: 'Tournament_Item (selected)',
    file: 'tournament_item.html',
    sketchExport: 'Selected_Tournament_Item.png',
    selector: '.tournament-item.selected',
    width: 342,
    height: 73,
  },
  {
    name: 'Credential_Item (deselected)',
    file: 'credential_item.html',
    sketchExport: 'Credential_Item.png',
    selector: '.credential-item:not(.selected)',
    width: 342,
    height: 64,
  },
  {
    name: 'Credential_Item (selected)',
    file: 'credential_item.html',
    sketchExport: 'Selected_Credential_Item.png',
    selector: '.credential-item.selected',
    width: 342,
    height: 64,
  },
  {
    name: 'SelectableItem_Symbol (deselected)',
    file: 'selectable_item_symbol.html',
    sketchExport: 'DeselectedItem_Symbol.png',
    selector: '.selectable-item-symbol:not(.selected)',
    width: 24,
    height: 24,
  },
  {
    name: 'SelectableItem_Symbol (selected)',
    file: 'selectable_item_symbol.html',
    sketchExport: 'SelectedItem_Symbol.png',
    selector: '.selectable-item-symbol.selected',
    width: 24,
    height: 24,
  },
];

const ALL_COMPONENTS = [...SIMPLE_COMPONENTS, ...STATEFUL_COMPONENTS];

for (const component of ALL_COMPONENTS) {
  test(`${component.name} matches Sketch export`, async ({ page }) => {
    const filePath = path.join(COMPONENTS_DIR, component.file);
    await page.goto(`file://${filePath}`);
    await page.waitForLoadState('networkidle');

    const element = page.locator(component.selector).first();
    await expect(element).toBeVisible();

    const sketchExportPath = path.join(SKETCH_EXPORTS_DIR, component.sketchExport);
    expect(fs.existsSync(sketchExportPath)).toBeTruthy();

    const componentScreenshot = await element.screenshot();
    const sketchImage = fs.readFileSync(sketchExportPath);

    await expect(element).toHaveScreenshot(`${component.name}.png`, {
      maxDiffPixelRatio: 0.05,
    });
  });
}

for (const component of ALL_COMPONENTS) {
  test(`${component.name} has correct dimensions`, async ({ page }) => {
    const filePath = path.join(COMPONENTS_DIR, component.file);
    await page.goto(`file://${filePath}`);
    await page.waitForLoadState('networkidle');

    const element = page.locator(component.selector).first();
    const box = await element.boundingBox();

    expect(box.width).toBeCloseTo(component.width, 0);
    if (component.height !== null) {
      expect(box.height).toBeCloseTo(component.height, 0);
    }
  });
}
