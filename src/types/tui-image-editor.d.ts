declare module '@toast-ui/react-image-editor' {
  import * as React from 'react';
  
  export interface ImageEditorInstance {
    toDataURL(options?: { format?: string; quality?: number }): string;
    loadImageFromURL(url: string, name: string): void;
    rotate(angle: number): void;
    flipX(): void;
    flipY(): void;
    setZoomRatio(ratio: number): void;
    undo(): void;
    redo(): void;
  }

  export interface ImageEditorProps {
    includeUI?: {
      loadImage?: {
        path: string;
        name: string;
      };
      theme?: Record<string, string>;
      menu?: string[];
      initMenu?: string;
      uiSize?: {
        width?: string | number;
        height?: string | number;
      };
      menuBarPosition?: string;
    };
    cssMaxWidth?: number;
    cssMaxHeight?: number;
    usageStatistics?: boolean;
    selectionStyle?: {
      cornerSize?: number;
      rotatingPointOffset?: number;
    };
  }

  const ImageEditor: React.ForwardRefExoticComponent<
    ImageEditorProps & React.RefAttributes<{ getInstance(): ImageEditorInstance }>
  >;

  export default ImageEditor;
}

declare module 'tui-image-editor' {
  const ImageEditor: any;
  export = ImageEditor;
}
