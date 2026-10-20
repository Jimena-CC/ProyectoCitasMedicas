import * as React from 'react';
import { Slot } from '@radix-ui/react-slot';

import { PolymorphicComponentProps } from '@/lib/polymorphic';
import { recursiveCloneChildren } from '@/lib/recursive-clone-children';
import { tv, type VariantProps } from '@/lib/tv';

const BANNER_ROOT_NAME = 'BannerRoot';
const BANNER_CONTENT_NAME = 'BannerContent';
const BANNER_ICON_NAME = 'BannerIcon';
const BANNER_CLOSE_BUTTON_NAME = 'BannerCloseButton';

export const bannerVariants = tv({
  slots: {
    root: 'relative grid h-11 w-full grid-cols-[1fr_auto_1fr] items-center justify-center gap-3 px-3',
    content: 'col-start-2 flex items-center justify-center gap-3',
    icon: 'size-5 shrink-0',
    closeButton: 'ml-auto size-5',
  },
  variants: {
    variant: {
      filled: {},
      light: {},
      lighter: {},
      stroke: {
        root: 'bg-bg-white-0 text-text-strong-950 before:absolute before:bottom-0 before:h-px before:w-full before:bg-stroke-soft-200',
      },
    },
    status: {
      error: {},
      warning: {},
      success: {},
      information: {},
      feature: {},
    },
  },
  compoundVariants: [
    //#region closeButton
    {
      variant: 'filled',
      class: {
        closeButton: 'opacity-[.72]',
      },
    },
    {
      variant: ['light', 'lighter', 'stroke'],
      class: {
        closeButton: 'opacity-[.48]',
      },
    },
    //#endregion

    //#region status=error
    {
      variant: 'filled',
      status: 'error',
      class: {
        icon: 'text-static-white',
        root: 'bg-error-base text-static-white',
      },
    },
    {
      variant: 'light',
      status: 'error',
      class: {
        icon: 'text-error-base',
        root: 'bg-error-light text-text-strong-950',
      },
    },
    {
      variant: 'lighter',
      status: 'error',
      class: {
        icon: 'text-error-base',
        root: 'bg-error-lighter text-text-strong-950',
      },
    },
    {
      variant: 'stroke',
      status: 'error',
      class: {
        icon: 'text-error-base',
      },
    },
    //#endregion

    //#region status=warning
    {
      variant: 'filled',
      status: 'warning',
      class: {
        icon: 'text-static-white',
        root: 'bg-warning-base text-static-white',
      },
    },
    {
      variant: 'light',
      status: 'warning',
      class: {
        icon: 'text-warning-base',
        root: 'bg-warning-light text-text-strong-950',
      },
    },
    {
      variant: 'lighter',
      status: 'warning',
      class: {
        icon: 'text-warning-base',
        root: 'bg-warning-lighter text-text-strong-950',
      },
    },
    {
      variant: 'stroke',
      status: 'warning',
      class: {
        icon: 'text-warning-base',
      },
    },
    //#endregion

    //#region status=success
    {
      variant: 'filled',
      status: 'success',
      class: {
        icon: 'text-static-white',
        root: 'bg-success-base text-static-white',
      },
    },
    {
      variant: 'light',
      status: 'success',
      class: {
        icon: 'text-success-base',
        root: 'bg-success-light text-text-strong-950',
      },
    },
    {
      variant: 'lighter',
      status: 'success',
      class: {
        icon: 'text-success-base',
        root: 'bg-success-lighter text-text-strong-950',
      },
    },
    {
      variant: 'stroke',
      status: 'success',
      class: {
        icon: 'text-success-base',
      },
    },
    //#endregion

    //#region status=information
    {
      variant: 'filled',
      status: 'information',
      class: {
        icon: 'text-static-white',
        root: 'bg-information-base text-static-white',
      },
    },
    {
      variant: 'light',
      status: 'information',
      class: {
        icon: 'text-information-base',
        root: 'bg-information-light text-text-strong-950',
      },
    },
    {
      variant: 'lighter',
      status: 'information',
      class: {
        icon: 'text-information-base',
        root: 'bg-information-lighter text-text-strong-950',
      },
    },
    {
      variant: 'stroke',
      status: 'information',
      class: {
        icon: 'text-information-base',
      },
    },
    //#endregion

    //#region status=feature
    {
      variant: 'filled',
      status: 'feature',
      class: {
        icon: 'text-static-white',
        root: 'bg-faded-base text-static-white',
      },
    },
    {
      variant: 'light',
      status: 'feature',
      class: {
        icon: 'text-faded-base',
        root: 'bg-faded-light text-text-strong-950',
      },
    },
    {
      variant: 'lighter',
      status: 'feature',
      class: {
        icon: 'text-faded-base',
        root: 'bg-faded-lighter text-text-strong-950',
      },
    },
    {
      variant: 'stroke',
      status: 'feature',
      class: {
        icon: 'text-faded-base',
      },
    },
    //#endregion
  ],
  defaultVariants: {
    variant: 'filled',
    status: 'feature',
  },
});

type BannerSharedProps = VariantProps<typeof bannerVariants>;

type BannerProps = VariantProps<typeof bannerVariants> &
  React.HTMLAttributes<HTMLDivElement>;

function Banner({
  children,
  className,
  variant,
  status,
  ...rest
}: BannerProps) {
  const uniqueId = React.useId();
  const { root } = bannerVariants({ variant, status });

  const sharedProps: BannerSharedProps = {
    variant,
    status,
  };

  const extendedChildren = recursiveCloneChildren(
    children as React.ReactElement[],
    sharedProps,
    [BANNER_ICON_NAME, BANNER_CLOSE_BUTTON_NAME],
    uniqueId,
  );

  return (
    <div className={root({ class: className })} {...rest}>
      {extendedChildren}
    </div>
  );
}
Banner.displayName = BANNER_ROOT_NAME;

function BannerContent({
  className,
  ...rest
}: React.HTMLAttributes<HTMLDivElement>) {
  const { content } = bannerVariants();

  return <div className={content({ class: className })} {...rest} />;
}
BannerContent.displayName = BANNER_CONTENT_NAME;

type BannerIconProps = BannerSharedProps & React.HTMLAttributes<HTMLDivElement>;

function BannerIcon<T extends React.ElementType>({
  className,
  variant,
  status,
  as,
  ...rest
}: PolymorphicComponentProps<T, BannerIconProps>) {
  const Component = as || 'div';
  const { icon } = bannerVariants({ variant, status });

  return <Component className={icon({ class: className })} {...rest} />;
}
BannerIcon.displayName = BANNER_ICON_NAME;

type BannerCloseButtonProps = BannerSharedProps &
  React.ButtonHTMLAttributes<HTMLButtonElement> & {
    asChild?: boolean;
  };

const BannerCloseButton = React.forwardRef<
  HTMLButtonElement,
  BannerCloseButtonProps
>(
  (
    { asChild, children, variant, status, className, ...rest },
    forwardedRef,
  ) => {
    const Component = asChild ? Slot : 'button';
    const { closeButton } = bannerVariants({ variant, status });

    return (
      <Component
        ref={forwardedRef}
        className={closeButton({ class: className })}
        {...rest}
      >
        {children}
      </Component>
    );
  },
);
BannerCloseButton.displayName = BANNER_CLOSE_BUTTON_NAME;

export {
  Banner as Root,
  BannerContent as Content,
  BannerIcon as Icon,
  BannerCloseButton as CloseButton,
};
